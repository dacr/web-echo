package webecho.security

import com.auth0.jwk.{JwkProvider, JwkProviderBuilder}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtOptions}
import webecho.SecurityConfig

import java.net.{URI, URL}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import java.util.Base64
import java.nio.charset.StandardCharsets
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import webecho.tools.JsonSupport.given
import org.slf4j.LoggerFactory
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

case class TokenResponse(access_token: String)

class SecurityService(config: SecurityConfig)(implicit system: ActorSystem) {
  import system.dispatcher
  private val logger = LoggerFactory.getLogger(getClass)

  implicit val tokenResponseCodec: JsonValueCodec[TokenResponse] = JsonCodecMaker.make

  private val provider: Option[JwkProvider] = if (config.keycloak.enabled) {
    Try(URI.create(config.keycloak.jwksUrl).toURL).toOption.map { url =>
      new JwkProviderBuilder(url)
        .cached(10, 24, java.util.concurrent.TimeUnit.HOURS)
        .build()
    }
  } else {
    None
  }

  def validate(token: String): Future[Either[String, UserProfile]] = {
    if (config.keycloak.enabled && provider.isDefined) {
      validateJwt(token)
    } else {
      if (!config.keycloak.enabled) {
        Future.successful(Right(UserProfile(Set.empty)))
      } else {
        Future.successful(Left("Authentication failed"))
      }
    }
  }

  def exchangeCodeForToken(code: String, redirectUri: String): Future[Option[String]] = {
    if (!config.keycloak.enabled) return Future.successful(None)

    val tokenUrl = s"${config.keycloak.url.stripSuffix("/")}/realms/${config.keycloak.realm}/protocol/openid-connect/token"
    val clientId = config.keycloak.resource.getOrElse("web-echo")

    val formData = FormData(
      "grant_type"   -> "authorization_code",
      "client_id"    -> clientId,
      "code"         -> code,
      "redirect_uri" -> redirectUri
    ).toEntity

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = tokenUrl,
      entity = formData
    )

    Http().singleRequest(request).flatMap { response =>
      if (response.status == StatusCodes.OK) {
        Unmarshal(response.entity).to[String].map { body =>
          Try(readFromString[TokenResponse](body).access_token).toOption
        }
      } else {
        response.discardEntityBytes()
        Future.successful(None)
      }
    }
  }

  private def getKid(token: String): Try[String] = {
    Try {
      val parts      = token.split("\\.")
      if (parts.length < 2) throw new Exception("Invalid JWT format")
      val headerJson = new String(java.util.Base64.getUrlDecoder.decode(parts(0)), StandardCharsets.UTF_8)
      val headerMap  = readFromString[Map[String, Any]](headerJson)
      headerMap.getOrElse("kid", throw new Exception("No 'kid' in header")).toString
    }
  }

  private def validateJwt(token: String): Future[Either[String, UserProfile]] = {
    Future {
      val result = for {
        kid       <- getKid(token)
        jwk       <- Try(provider.get.get(kid))
        publicKey <- Try(jwk.getPublicKey)
        options    = JwtOptions(signature = true, expiration = true, notBefore = true)
        claim     <- Jwt.decode(token, publicKey, Seq(JwtAlgorithm.RS256), options) // Decode and verify signature
        _         <- if (claim.issuer.contains(config.keycloak.issuer)) {
                       // Verify Issuer
                       Success(())
                     } else {
                       val msg = s"Issuer mismatch. Expected: ${config.keycloak.issuer}, Got: ${claim.issuer.getOrElse("")}"
                       if (config.keycloak.strictIssuerCheck) {
                         Failure(new Exception(msg))
                       } else {
                         logger.warn(msg)
                         Success(())
                       }
                     }
        _         <- config.keycloak.resource match {
                       // Verify Audience / Resource (Optional)
                       // Keycloak puts the client_id in 'azp' (Authorized Party) or 'aud'
                       // If config.keycloak.resource is set, we check if it is present in aud or azp (if claims supported)
                       case Some(res) =>
                         if (claim.audience.exists(_.contains(res))) Success(())
                         else {
                           // Fallback: check 'azp' (Authorized Party) claim which Keycloak often uses for client_id
                           Try {
                             val contentMap = readFromString[Map[String, Any]](claim.content)
                             contentMap.get("azp").collect { case s: String => s }
                           }.toOption.flatten match {
                             case Some(azp) if azp == res => Success(())
                             case _                       => Failure(new Exception(s"Invalid audience. Expected: $res"))
                           }
                         }
                       case None      => Success(())
                     }
        contentMap <- Try(readFromString[Map[String, Any]](claim.content))
        roles      = extractRoles(contentMap)
      } yield UserProfile(roles)

      result match {
        case Success(p) => Right(p)
        case Failure(e) => Left(s"JWT Validation failed: ${e.getMessage}")
      }
    }
  }

  private def extractRoles(claims: Map[String, Any]): Set[String] = {
    val realmRoles = claims.get("realm_access").collect {
      case access: Map[_, _] =>
        access.asInstanceOf[Map[String, Any]].get("roles").collect {
          case roles: List[_] => roles.map(_.toString).toSet
        }.getOrElse(Set.empty[String])
    }.getOrElse(Set.empty[String])
    realmRoles
  }
}

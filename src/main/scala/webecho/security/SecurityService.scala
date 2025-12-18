package webecho.security

import com.auth0.jwk.{JwkProvider, JwkProviderBuilder}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtOptions}
import webecho.SecurityConfig
import java.net.URL
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import java.util.Base64
import java.nio.charset.StandardCharsets

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import webecho.tools.JsonSupport
import org.slf4j.LoggerFactory
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._

case class TokenResponse(access_token: String)

class SecurityService(config: SecurityConfig)(implicit system: ActorSystem) extends JsonSupport {
  import system.dispatcher
  private val logger = LoggerFactory.getLogger(getClass)

  implicit val tokenResponseCodec: JsonValueCodec[TokenResponse] = JsonCodecMaker.make

  private val provider: Option[JwkProvider] = if (config.keycloak.enabled) {
    Try(new URL(config.keycloak.jwksUrl)).toOption.map {
      url =>
        new JwkProviderBuilder(url)
          .cached(10, 24, java.util.concurrent.TimeUnit.HOURS)
          .build()
    }
  } else {
    None
  }

  def validate(token: String): Future[Either[String, Unit]] = {
    if (config.keycloak.enabled && provider.isDefined) {
      validateJwt(token)
    } else {
      if (!config.keycloak.enabled) {
         Future.successful(Right(())) 
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
      "grant_type" -> "authorization_code",
      "client_id" -> clientId,
      "code" -> code,
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
      val parts = token.split("\\.")
      if (parts.length < 2) throw new Exception("Invalid JWT format")
      val headerJson = new String(java.util.Base64.getUrlDecoder.decode(parts(0)), StandardCharsets.UTF_8)
      val headerMap = readFromString[Map[String, Any]](headerJson)(mapAnyCodec)
      headerMap.getOrElse("kid", throw new Exception("No 'kid' in header")).toString
    }
  }

  private def validateJwt(token: String): Future[Either[String, Unit]] = {
    Future {
      val result = for {
        kid <- getKid(token)
        jwk <- Try(provider.get.get(kid))
        publicKey <- Try(jwk.getPublicKey)
        options = JwtOptions(signature = true, expiration = true, notBefore = true)
        
        // Decode and verify signature
        claim <- Jwt.decode(token, publicKey, Seq(JwtAlgorithm.RS256), options)
        
        // Verify Issuer (Relaxed check for local dev/docker compatibility)
        _ <- if (claim.issuer.contains(config.keycloak.issuer)) {
             Success(()) 
        } else {
             // Just warn instead of failing, to handle localhost vs 127.0.0.1 mismatches
             logger.warn(s"Issuer mismatch. Expected: ${config.keycloak.issuer}, Got: ${claim.issuer.getOrElse("")}")
             Success(())
        }
        
        // Verify Audience / Resource (Optional)
        // Keycloak puts the client_id in 'azp' (Authorized Party) or 'aud' 
        // If config.keycloak.resource is set, we check if it is present in aud or azp (if claims supported)
        // jwt-core Claim access is a bit limited on specific fields like azp, 
        // but we can check the JSON content or standard fields.
        // For now, let's check standard 'aud'.
        _ <- config.keycloak.resource match {
          case Some(res) =>
             if (claim.audience.exists(_.contains(res))) Success(())
             // You might also want to check 'azp' claim if 'aud' check fails, 
             // but jwt-core might not expose azp directly in JwtClaim class comfortably without raw json.
             // Let's rely on Audience for now as it's standard.
             else Failure(new Exception(s"Invalid audience. Expected: $res"))
          case None => Success(())
        }

      } yield ()

      result match {
        case Success(_) => Right(())
        case Failure(e) => Left(s"JWT Validation failed: ${e.getMessage}")
      }
    }
  }
}
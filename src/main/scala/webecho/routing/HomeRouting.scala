package webecho.routing

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.model.MediaTypes.{`text/html`, `image/png`}
import org.apache.pekko.http.scaladsl.model.HttpCharsets._
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import webecho.ServiceDependencies
import webecho.model.{StoreInfo, Origin}
import webecho.templates.html.{HomeTemplate, RecorderTemplate, RecordedDataTemplate}
import webecho.tools.{UniqueIdentifiers, QRCodeGenerator}
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory

case class HomePageContext(page: PageContext, stats: StoreInfo)
case class RecorderPageContext(page: PageContext, fullRecorderUrl: String, baseRecorderUrl: String, viewDataUrl: String, message: Option[String])
case class RecordedDataPageContext(page: PageContext, recorderPageUrl: String, dataApiUrl: String)

import org.apache.pekko.http.scaladsl.model.headers.HttpCookie

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  private val logger = LoggerFactory.getLogger(getClass)
  override def routes: Route = concat(home, createRecorder, showRecorder, showRecordedData, qrcode, login, logout, callback)

  val site = dependencies.config.webEcho.site
  val keycloak = dependencies.config.webEcho.security.keycloak
  val clientId = keycloak.resource.getOrElse("web-echo")

  private def getRedirectUri(uri: org.apache.pekko.http.scaladsl.model.Uri): String = {
    val host = uri.authority.host.address()
    val port = if (uri.authority.port != 0) s":${uri.authority.port}" else ""
    val prefix = dependencies.config.webEcho.site.absolutePrefix
    s"${uri.scheme}://$host$port$prefix/callback"
  }

  // Helper to create context with login state
  private def getPageContext(isLoggedIn: Boolean): PageContext = {
    PageContext(dependencies.config.webEcho, isLoggedIn)
  }

  def home: Route = pathEndOrSingleSlash {
    get {
      optionalCookie("X-Auth-Token") { cookie =>
        val isLoggedIn = cookie.exists(_.value.nonEmpty)
        complete {
          val statsOption     = dependencies.echoStore.storeInfo()
          val stats           = statsOption.getOrElse(StoreInfo(lastUpdated = None, count = 0))
          val homePageContext = HomePageContext(getPageContext(isLoggedIn), stats)
          val content         = HomeTemplate.render(homePageContext).toString()
          val contentType     = `text/html` withCharset `UTF-8`
          HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
        }
      }
    }
  }

  def login: Route = path("login") {
    get {
      if (keycloak.enabled) {
        extractUri { uri =>
          val currentRedirectUri = getRedirectUri(uri)
          val encodedRedirectUri = java.net.URLEncoder.encode(currentRedirectUri, "UTF-8")
          val authUrl = s"${keycloak.url.stripSuffix("/")}/realms/${keycloak.realm}/protocol/openid-connect/auth" +
            s"?client_id=$clientId&response_type=code&redirect_uri=$encodedRedirectUri"
          logger.debug(s"Login route hit. Redirecting to Keycloak: $authUrl")
          redirect(authUrl, StatusCodes.SeeOther)
        }
      } else {
        logger.debug("Login route hit. Security disabled. Redirecting to Home.")
        redirect("/", StatusCodes.SeeOther)
      }
    }
  }

  def logout: Route = path("logout") {
    get {
      deleteCookie("X-Auth-Token", path = "/") {
        if (keycloak.enabled) {
          extractUri { uri =>
            val host = uri.authority.host.address()
            val port = if (uri.authority.port != 0) s":${uri.authority.port}" else ""
            val prefix = dependencies.config.webEcho.site.absolutePrefix
            val redirectUri = s"${uri.scheme}://$host$port$prefix/"
            val encodedRedirectUri = java.net.URLEncoder.encode(redirectUri, "UTF-8")
            
            val logoutUrl = s"${keycloak.url.stripSuffix("/")}/realms/${keycloak.realm}/protocol/openid-connect/logout" +
              s"?post_logout_redirect_uri=$encodedRedirectUri&client_id=$clientId"
            
            logger.debug(s"Logout route hit. Redirecting to Keycloak logout: $logoutUrl")
            redirect(logoutUrl, StatusCodes.SeeOther)
          }
        } else {
          logger.debug("Logout route hit. Security disabled. Redirecting to Home.")
          redirect("/", StatusCodes.SeeOther)
        }
      }
    }
  }

  def callback: Route = path("callback") {
    get {
      parameters("code") { code =>
        extractUri { uri =>
          val currentRedirectUri = getRedirectUri(uri)
          logger.debug("Callback hit with code.")
          onSuccess(dependencies.securityService.exchangeCodeForToken(code, currentRedirectUri)) {
            case Some(token) =>
              logger.debug("Token exchanged successfully.")
              setCookie(HttpCookie("X-Auth-Token", value = token, path = Some("/"), httpOnly = true)) {
                optionalCookie("Login-State") { stateCookie =>
                  deleteCookie("Login-State", path = "/") {
                    if (stateCookie.map(_.value).contains("create")) {
                      logger.debug("Performing automatic recorder creation based on cookie.")
                      performCreateRecorder
                    } else {
                      logger.debug("Redirecting to Home.")
                      redirect("/", StatusCodes.Found)
                    }
                  }
                }
              }
            case None =>
              logger.debug("Token exchange failed.")
              complete(StatusCodes.Unauthorized, "Login failed")
          }
        }
      }
    }
  }

  def createRecorder: Route = path("recorder") {
    post {
      optionalCookie("X-Auth-Token") { cookie =>
        val token = cookie.map(_.value).getOrElse("")
        onSuccess(dependencies.securityService.validate(token)) {
          case Right(_) =>
            logger.debug("createRecorder - Validation success.")
            performCreateRecorder
          case Left(msg) =>
            logger.debug(s"createRecorder - Validation failed ($msg). Setting intent cookie and redirecting to login.")
            setCookie(HttpCookie("Login-State", "create", path = Some("/"), httpOnly = true)) {
              redirect("/login", StatusCodes.SeeOther)
            }
        }
      }
    }
  }

  private def performCreateRecorder: Route = {
    extractClientIP { ip =>
      extractRequest { request =>
        val userAgent = request.headers.find(_.name() == "User-Agent").map(_.value())
        val uuid   = UniqueIdentifiers.randomUUID()
        val origin = Origin(
          createdOn = OffsetDateTime.now(),
          createdByIpAddress = ip.toOption.map(_.getHostAddress),
          createdByUserAgent = userAgent
        )
        dependencies.echoStore.echoAdd(id = uuid, description = None, origin = Some(origin))
        // Redirect to the show page
        redirect(s"${site.baseURL}/recorder/$uuid", StatusCodes.SeeOther)
      }
    }
  }

  def showRecorder: Route = path("recorder" / Segment) { uuidStr =>
    get {
      optionalCookie("X-Auth-Token") { cookie =>
        val isLoggedIn = cookie.exists(_.value.nonEmpty)
        parameter("message".?) { message =>
          UniqueIdentifiers.fromString(uuidStr) match {
            case scala.util.Success(uuid) =>
              dependencies.echoStore.echoInfo(uuid) match {
                case Some(_) =>
                  val baseRecorderUrl = s"${site.apiURL}/recorder/$uuid"
                  val fullRecorderUrl = message.filter(_.nonEmpty) match {
                    case Some(msg) => s"$baseRecorderUrl?message=$msg"
                    case None      => baseRecorderUrl
                  }
                  val viewDataUrl = s"${site.baseURL}/recorder/$uuid/view"
                
                  val ctx = RecorderPageContext(getPageContext(isLoggedIn), fullRecorderUrl, baseRecorderUrl, viewDataUrl, message)
                  val content = RecorderTemplate.render(ctx).toString()
                  val contentType = `text/html` withCharset `UTF-8`
                  complete(HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders))
                case None =>
                  complete(StatusCodes.NotFound, "Recorder not found")
              }
            case scala.util.Failure(_) =>
              complete(StatusCodes.BadRequest, "Invalid UUID")
          }
        }
      }
    }
  }

  def showRecordedData: Route = path("recorder" / Segment / "view") { uuidStr =>
    get {
      optionalCookie("X-Auth-Token") { cookie =>
        val isLoggedIn = cookie.exists(_.value.nonEmpty)
        parameter("message".?) { message =>
          UniqueIdentifiers.fromString(uuidStr) match {
            case scala.util.Success(uuid) =>
               dependencies.echoStore.echoInfo(uuid) match {
                case Some(_) =>
                  val baseRecorderPageUrl = s"${site.baseURL}/recorder/$uuid"
                  val recorderPageUrl = message.filter(_.nonEmpty) match {
                    case Some(msg) => s"$baseRecorderPageUrl?message=$msg"
                    case None      => baseRecorderPageUrl
                  }
                
                  val dataApiUrl = s"${site.apiURL}/recorder/$uuid/records"
                  val ctx = RecordedDataPageContext(getPageContext(isLoggedIn), recorderPageUrl, dataApiUrl)
                  val content = RecordedDataTemplate.render(ctx).toString()
                  val contentType = `text/html` withCharset `UTF-8`
                  complete(HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders))
                case None =>
                  complete(StatusCodes.NotFound, "Recorder not found")
              }
            case scala.util.Failure(_) =>
               complete(StatusCodes.BadRequest, "Invalid UUID")
          }
        }
      }
    }
  }

  def qrcode: Route = path("qrcode") {
    get {
      parameter("text") { text =>
        complete(HttpEntity(`image/png`, QRCodeGenerator.generateQRCodePng(text)))
      }
    }
  }
}

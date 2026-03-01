package webecho.routing

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.model.MediaTypes.{`text/html`, `image/png`}
import org.apache.pekko.http.scaladsl.model.HttpCharsets._
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import webecho.ServiceDependencies
import webecho.model.{StoreInfo, Origin, EchoInfo}
import webecho.templates.html.{HomeTemplate, RecorderTemplate, RecordedDataTemplate, PendingAccountTemplate, RecorderEditTemplate}
import webecho.tools.{UniqueIdentifiers, QRCodeGenerator}
import webecho.logic.{CommandContext, AccountPending, LogicError, RecorderNotFound, AccessDenied, InvalidInput, SystemError}
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import scala.concurrent.duration.Duration
import webecho.security.UserProfile

case class HomePageContext(page: PageContext, stats: StoreInfo)
case class RecorderPageContext(page: PageContext, recorderId: String, fullRecorderUrl: String, baseRecorderUrl: String, viewDataUrl: String, message: Option[String], recorderInfo: EchoInfo)
case class RecorderEditPageContext(page: PageContext, recorderId: String, description: Option[String], lifeExpectancy: Option[String])
case class RecordedDataPageContext(page: PageContext, recorderPageUrl: String, dataApiUrl: String)

import org.apache.pekko.http.scaladsl.model.headers.HttpCookie

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  private val logger = LoggerFactory.getLogger(getClass)
  private val logic  = dependencies.logic
  import dependencies.system.dispatcher // Import ExecutionContext

  override def routes: Route = concat(home, pending, createRecorder, showRecorder, editRecorder, updateRecorder, showRecordedData, qrcode, login, logout, callback)

  val site     = dependencies.config.webEcho.site
  val keycloak = dependencies.config.webEcho.security.keycloak
  val clientId = keycloak.resource.getOrElse("web-echo")

  private def getRedirectUri(uri: org.apache.pekko.http.scaladsl.model.Uri, xForwardedProto: Option[String]): String = {
    val host   = uri.authority.host.address()
    val port   = if (uri.authority.port != 0) s":${uri.authority.port}" else ""
    val prefix = dependencies.config.webEcho.site.absolutePrefix
    val scheme = xForwardedProto.getOrElse(uri.scheme)
    s"$scheme://$host$port$prefix/callback"
  }

  // Helper to create context with login state
  private def getPageContext(isLoggedIn: Boolean): PageContext = {
    PageContext(dependencies.config.webEcho, isLoggedIn)
  }

  // Helper to build CommandContext from request
  private def withCommandContext(fn: CommandContext => Route): Route = {
    optionalCookie("X-Auth-Token") { cookie =>
      extractClientIP { ip =>
        extractRequest { request =>
          val userAgent = request.headers.find(_.name() == "User-Agent").map(_.value())
          val clientIP  = ip.toOption.map(_.getHostAddress)

          val futureProfile = cookie match {
            case Some(c) if c.value.nonEmpty => dependencies.securityService.validate(c.value).map(_.toOption)
            case _                           => scala.concurrent.Future.successful(None)
          }

          onSuccess(futureProfile) { profile =>
            val ctx = CommandContext(profile, clientIP, userAgent)
            fn(ctx)
          }
        }
      }
    }
  }

  private def handleLogicResult[T](result: Either[LogicError, T], intent: Option[String] = None)(onSuccess: T => Route): Route = result match {
    case Right(value)              => onSuccess(value)
    case Left(AccountPending)      => redirect(s"${site.baseURL}/pending", StatusCodes.SeeOther)
    case Left(AccessDenied(_))     =>
      intent match {
        case Some(state) =>
          setCookie(HttpCookie("Login-State", state, path = Some("/"), httpOnly = true)) {
            redirect("/login", StatusCodes.SeeOther)
          }
        case None        => complete(StatusCodes.Unauthorized, "Unauthorized")
      }
    case Left(RecorderNotFound(_)) => complete(StatusCodes.NotFound, "Recorder not found")
    case Left(err)                 => complete(StatusCodes.InternalServerError, err.toString)
  }

  def home: Route = pathEndOrSingleSlash {
    withCommandContext { ctx =>
      if (ctx.user.exists(_.isPending)) {
        redirect(s"${site.baseURL}/pending", StatusCodes.SeeOther)
      } else {
        renderHome(ctx.user.isDefined)
      }
    }
  }

  private def renderHome(isLoggedIn: Boolean): Route = {
    implicit val ctx = CommandContext(None, None, None) // Public info
    onSuccess(logic.getServiceInfo()) { result =>
      complete {
        val stats           = result.getOrElse(StoreInfo(lastUpdated = None, count = 0))
        val homePageContext = HomePageContext(getPageContext(isLoggedIn), stats)
        val content         = HomeTemplate.render(homePageContext).toString()
        val contentType     = `text/html` withCharset `UTF-8`
        HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
      }
    }
  }

  def pending: Route = path("pending") {
    get {
      val ctx     = getPageContext(true)
      val content = PendingAccountTemplate.render(ctx).toString()
      complete(HttpEntity(`text/html` withCharset `UTF-8`, content))
    }
  }

  def login: Route = path("login") {
    get {
      if (keycloak.enabled) {
        extractUri { uri =>
          optionalHeaderValueByName("X-Forwarded-Proto") { proto =>
            val currentRedirectUri = getRedirectUri(uri, proto)
            val encodedRedirectUri = java.net.URLEncoder.encode(currentRedirectUri, "UTF-8")
            val authUrl            = s"${keycloak.url.stripSuffix("/")}/realms/${keycloak.realm}/protocol/openid-connect/auth" +
              s"?client_id=$clientId&response_type=code&redirect_uri=$encodedRedirectUri"
            logger.debug(s"Login route hit. Redirecting to Keycloak: $authUrl")
            redirect(authUrl, StatusCodes.SeeOther)
          }
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
            optionalHeaderValueByName("X-Forwarded-Proto") { proto =>
              val host               = uri.authority.host.address()
              val port               = if (uri.authority.port != 0) s":${uri.authority.port}" else ""
              val prefix             = dependencies.config.webEcho.site.absolutePrefix
              val scheme             = proto.getOrElse(uri.scheme)
              val redirectUri        = s"$scheme://$host$port$prefix/"
              val encodedRedirectUri = java.net.URLEncoder.encode(redirectUri, "UTF-8")

              val logoutUrl = s"${keycloak.url.stripSuffix("/")}/realms/${keycloak.realm}/protocol/openid-connect/logout" +
                s"?post_logout_redirect_uri=$encodedRedirectUri&client_id=$clientId"

              logger.debug(s"Logout route hit. Redirecting to Keycloak logout: $logoutUrl")
              redirect(logoutUrl, StatusCodes.SeeOther)
            }
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
          optionalHeaderValueByName("X-Forwarded-Proto") { proto =>
            val currentRedirectUri = getRedirectUri(uri, proto)
            logger.debug("Callback hit with code.")
            onSuccess(dependencies.securityService.exchangeCodeForToken(code, currentRedirectUri)) {
              case Some(token) =>
                logger.debug("Token exchanged successfully.")
                onSuccess(dependencies.securityService.validate(token)) {
                  case Right(profile) if profile.isPending =>
                    setCookie(HttpCookie("X-Auth-Token", value = token, path = Some("/"), httpOnly = true)) {
                      deleteCookie("Login-State", path = "/") {
                        redirect(s"${site.baseURL}/pending", StatusCodes.SeeOther)
                      }
                    }
                  case Right(profile)                      =>
                    setCookie(HttpCookie("X-Auth-Token", value = token, path = Some("/"), httpOnly = true)) {
                      optionalCookie("Login-State") { stateCookie =>
                        deleteCookie("Login-State", path = "/") {
                          if (stateCookie.map(_.value).contains("create")) {
                            logger.debug("Performing automatic recorder creation based on cookie.")
                            extractClientIP { ip =>
                              extractRequest { request =>
                                val userAgent                    = request.headers.find(_.name() == "User-Agent").map(_.value())
                                val clientIP                     = ip.toOption.map(_.getHostAddress)
                                implicit val ctx: CommandContext = CommandContext(Some(profile), clientIP, userAgent)
                                onSuccess(logic.createRecorder) { result =>
                                  handleLogicResult(result, Some("create")) { recorder =>
                                    redirect(s"${site.baseURL}/recorder/${recorder.id}", StatusCodes.SeeOther)
                                  }
                                }
                              }
                            }
                          } else if (stateCookie.map(_.value).exists(_.startsWith("edit:"))) {
                            val uuidStr = stateCookie.get.value.stripPrefix("edit:")
                            logger.debug(s"Redirecting to edit recorder $uuidStr.")
                            redirect(s"/recorder/$uuidStr/edit", StatusCodes.Found)
                          } else {
                            logger.debug("Redirecting to Home.")
                            redirect("/", StatusCodes.Found)
                          }
                        }
                      }
                    }
                  case Left(_)                             =>
                    complete(StatusCodes.Unauthorized, "Login failed (validation)")
                }
              case None        =>
                logger.debug("Token exchange failed.")
                complete(StatusCodes.Unauthorized, "Login failed")
            }
          }
        }
      }
    }
  }

  def createRecorder: Route = path("recorder") {
    post {
      withCommandContext { implicit ctx =>
        onSuccess(logic.createRecorder) { result =>
          handleLogicResult(result, Some("create")) { recorder =>
            redirect(s"${site.baseURL}/recorder/${recorder.id}", StatusCodes.SeeOther)
          }
        }
      }
    }
  }

  def showRecorder: Route = path("recorder" / Segment) { uuidStr =>
    get {
      withCommandContext { implicit ctx =>
        parameter("message".?) { message =>
          UniqueIdentifiers.fromString(uuidStr) match {
            case scala.util.Success(uuid) =>
              onSuccess(logic.getRecorder(uuid)) { result =>
                handleLogicResult(result) { info =>
                  val baseRecorderUrl = s"${site.apiURL}/record/$uuid"
                  val fullRecorderUrl = message.filter(_.nonEmpty) match {
                    case Some(msg) => s"$baseRecorderUrl?message=$msg"
                    case None      => baseRecorderUrl
                  }
                  val viewDataUrl     = s"${site.baseURL}/recorder/$uuid/view"

                  val pageCtx     = RecorderPageContext(getPageContext(ctx.user.isDefined), uuidStr, fullRecorderUrl, baseRecorderUrl, viewDataUrl, message, info)
                  val content     = RecorderTemplate.render(pageCtx).toString()
                  val contentType = `text/html` withCharset `UTF-8`
                  complete(HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders))
                }
              }
            case scala.util.Failure(_)    =>
              complete(StatusCodes.BadRequest, "Invalid UUID")
          }
        }
      }
    }
  }

  def editRecorder: Route = path("recorder" / Segment / "edit") { uuidStr =>
    get {
      withCommandContext { implicit ctx =>
        logic.checkAccess match {
          case Left(AccountPending)  =>
            redirect(s"${site.baseURL}/pending", StatusCodes.SeeOther)
          case Left(AccessDenied(_)) =>
            setCookie(HttpCookie("Login-State", s"edit:$uuidStr", path = Some("/"), httpOnly = true)) {
              redirect("/login", StatusCodes.SeeOther)
            }
          case Right(_)              =>
            UniqueIdentifiers.fromString(uuidStr) match {
              case scala.util.Success(uuid) =>
                onSuccess(logic.getRecorder(uuid)) { result =>
                  handleLogicResult(result) { info =>
                    val pageCtx = RecorderEditPageContext(
                      getPageContext(true),
                      uuidStr,
                      info.description,
                      info.lifeExpectancy.map(_.toString)
                    )
                    val content = RecorderEditTemplate.render(pageCtx).toString()
                    complete(HttpEntity(`text/html` withCharset `UTF-8`, content))
                  }
                }
              case scala.util.Failure(_)    =>
                complete(StatusCodes.BadRequest, "Invalid UUID")
            }
        }
      }
    }
  }

  def updateRecorder: Route = path("recorder" / Segment / "edit") { uuidStr =>
    post {
      withCommandContext { implicit ctx =>
        formFields("description".?, "lifeExpectancy".?) { (description, lifeExpectancyStr) =>
          UniqueIdentifiers.fromString(uuidStr) match {
            case scala.util.Success(uuid) =>
              val lifeExpectancy      = lifeExpectancyStr.flatMap { s =>
                if (s.trim.isEmpty) None else scala.util.Try(Duration(s)).toOption
              }
              val finalDescription    = description.filter(_.trim.nonEmpty)
              val finalLifeExpectancy = lifeExpectancy.collect { case fd: scala.concurrent.duration.FiniteDuration => fd }

              onSuccess(logic.updateRecorder(uuid, finalDescription, finalLifeExpectancy)) { result =>
                handleLogicResult(result) { _ =>
                  redirect(s"${site.baseURL}/recorder/$uuidStr", StatusCodes.SeeOther)
                }
              }
            case scala.util.Failure(_)    =>
              complete(StatusCodes.BadRequest, "Invalid UUID")
          }
        }
      }
    }
  }

  def showRecordedData: Route = path("recorder" / Segment / "view") { uuidStr =>
    get {
      withCommandContext { implicit ctx =>
        parameter("message".?) { message =>
          UniqueIdentifiers.fromString(uuidStr) match {
            case scala.util.Success(uuid) =>
              onSuccess(logic.getRecorder(uuid)) { result =>
                handleLogicResult(result) { _ =>
                  val baseRecorderPageUrl = s"${site.baseURL}/recorder/$uuid"
                  val recorderPageUrl     = message.filter(_.nonEmpty) match {
                    case Some(msg) => s"$baseRecorderPageUrl?message=$msg"
                    case None      => baseRecorderPageUrl
                  }

                  val dataApiUrl  = s"${site.apiURL}/recorder/$uuid/records"
                  val pageCtx     = RecordedDataPageContext(getPageContext(ctx.user.isDefined), recorderPageUrl, dataApiUrl)
                  val content     = RecordedDataTemplate.render(pageCtx).toString()
                  val contentType = `text/html` withCharset `UTF-8`
                  complete(HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders))
                }
              }
            case scala.util.Failure(_)    =>
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

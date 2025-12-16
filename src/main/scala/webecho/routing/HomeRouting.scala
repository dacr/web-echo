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

case class HomePageContext(page: PageContext, stats: StoreInfo)
case class RecorderPageContext(page: PageContext, fullRecorderUrl: String, baseRecorderUrl: String, viewDataUrl: String, message: Option[String])
case class RecordedDataPageContext(page: PageContext, recorderPageUrl: String, dataApiUrl: String)

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  override def routes: Route = concat(home, createRecorder, showRecorder, showRecordedData, qrcode)

  val site = dependencies.config.webEcho.site

  val pageContext = PageContext(dependencies.config.webEcho)

  def home: Route = pathEndOrSingleSlash {
    get {
      complete {
        val statsOption     = dependencies.echoStore.storeInfo()
        val stats           = statsOption.getOrElse(StoreInfo(lastUpdated = None, count = 0))
        val homePageContext = HomePageContext(pageContext, stats)
        val content         = HomeTemplate.render(homePageContext).toString()
        val contentType     = `text/html` withCharset `UTF-8`
        HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
      }
    }
  }

  def createRecorder: Route = path("recorder") {
    post {
      extractClientIP { ip =>
        extractRequest { request =>
          val userAgent = request.headers.find(_.name() == "User-Agent").map(_.value())
          val uuid   = UniqueIdentifiers.timedUUID()
          val origin = Origin(
            createdOn = OffsetDateTime.now(),
            createdByIpAddress = ip.toOption.map(_.getHostAddress),
            createdByUserAgent = userAgent
          )
          dependencies.echoStore.echoAdd(uuid, Some(origin))
          // Redirect to the show page
          redirect(s"${site.baseURL}/recorder/$uuid", StatusCodes.SeeOther)
        }
      }
    }
  }

  def showRecorder: Route = path("recorder" / Segment) { uuidStr =>
    get {
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
                
                val ctx = RecorderPageContext(pageContext, fullRecorderUrl, baseRecorderUrl, viewDataUrl, message)
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

  def showRecordedData: Route = path("recorder" / Segment / "view") { uuidStr =>
    get {
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
                val ctx = RecordedDataPageContext(pageContext, recorderPageUrl, dataApiUrl)
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

  def qrcode: Route = path("qrcode") {
    get {
      parameter("text") { text =>
        complete(HttpEntity(`image/png`, QRCodeGenerator.generateQRCodePng(text)))
      }
    }
  }
}

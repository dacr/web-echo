package webecho.routing

import akka.http.scaladsl.server.Directives.pathEndOrSingleSlash
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.json4s.{Extraction, JField, JObject, JValue}
import webecho.ServiceDependencies
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

import java.time.OffsetDateTime
import java.util.UUID

case class EchoRouting(dependencies: ServiceDependencies) extends Routing {

  val prefix = dependencies.config.webEcho.site.cleanedPrefix.map(p => s"/$p").getOrElse("")

  override def routes: Route = newEcho ~ getEcho ~ postEcho

  var receivedCache = Map.empty[String, List[JValue]]

  def newEcho: Route = {
    pathEndOrSingleSlash {
      get {
        val uuid = UUID.randomUUID().toString
        val uri = Uri(s"$prefix/echoed/$uuid")
        receivedCache += (uuid -> List.empty[JValue])
        redirect(uri, StatusCodes.TemporaryRedirect)
      }
    }
  }

  def getEcho: Route = {
    get {
      path("echoed" / JavaUUID) { uuid =>
        complete {
          receivedCache.get(uuid.toString)
        }
      }
    }
  }

  def postEcho: Route = {
    post {
      path("echoed" / JavaUUID) { uuid =>
        extractClientIP { clientIP =>
          entity(as[JValue]) { posted =>
            val key = uuid.toString
            receivedCache.get(key) match {
              case None =>
                complete(StatusCodes.Forbidden -> "Well tried ;)")
              case Some(alreadyPosted) =>
                val enriched = JObject(
                  JField("data", posted),
                  JField("timestamp", Extraction.decompose(OffsetDateTime.now())),
                  JField("client_ip", Extraction.decompose(clientIP.toOption.map(_.getHostAddress)))
                )

                receivedCache += key -> (enriched :: alreadyPosted)
                complete(StatusCodes.OK)
            }
          }
        }
      }
    }

  }
}

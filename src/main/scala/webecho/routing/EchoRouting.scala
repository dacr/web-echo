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

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.UUID

case class EchoRouting(dependencies: ServiceDependencies) extends Routing {

  val prefix = dependencies.config.webEcho.site.cleanedPrefix.map(p => s"/$p").getOrElse("")

  override def routes: Route = newEcho ~ getEcho ~ postEcho ~ info ~ echoInfo

  private val receivedCache = dependencies.echoCache

  def epochToUTCDateTime(epoch:Long):OffsetDateTime = {
    Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.UTC)
  }

  def info: Route = {
    get {
      path("info") {
        complete {
          Map(
            "entriesCount" -> receivedCache.entriesCount(),
            "lastUpdated" -> epochToUTCDateTime(receivedCache.lastUpdated().getOrElse(0L))
          )
        }
      }
    }
  }

  def newEcho: Route = {
    pathEndOrSingleSlash {
      get {
        val uuid = UUID.randomUUID()
        val uri = Uri(s"$prefix/echoed/${uuid.toString}")
        receivedCache.createEntry(uuid)
        redirect(uri, StatusCodes.TemporaryRedirect)
      }
    }
  }

  def getEcho: Route = {
    get {
      path("echoed" / JavaUUID) { uuid =>
        parameters("latest".optional, "count".as[Int].optional) { (latest, count) =>
          receivedCache.get(uuid) match {
            case None => complete(StatusCodes.Forbidden -> "Well tried ;)")
            case Some(entry) if latest.isDefined => complete(entry.content.take(1))
            case Some(entry) if count.isDefined && count.get>=0 => complete(entry.content.take(count.get))
            case Some(entry) => complete(entry.content)
          }
        }
      }
    }
  }


  def echoInfo: Route = {
    get {
      path("echoed" / JavaUUID / "info") { uuid =>
        receivedCache.get(uuid) match {
          case None =>
            complete(StatusCodes.Forbidden -> "Well tried ;)")
          case Some(entry) =>
            complete {
              Map(
                "echoCount" -> entry.content.size,
                "lastUpdated" -> epochToUTCDateTime(entry.lastUpdated)
              )
            }
        }
      }
    }
  }


  def postEcho: Route = {
    post {
      path("echoed" / JavaUUID) { uuid =>
        extractClientIP { clientIP =>
          if (!receivedCache.hasEntry(uuid)) {
            complete(StatusCodes.Forbidden -> "Well tried ;)")
          } else {
            entity(as[JValue]) { posted =>
              val enriched = JObject(
                JField("data", posted),
                JField("timestamp", Extraction.decompose(OffsetDateTime.now())),
                JField("client_ip", Extraction.decompose(clientIP.toOption.map(_.getHostAddress)))
              )
              receivedCache.prepend(uuid, enriched)
              complete(StatusCodes.OK)
            }
          }
        }
      }
    }
  }


}

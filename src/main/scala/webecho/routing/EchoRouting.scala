/*
 * Copyright 2021 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package webecho.routing

import akka.NotUsed
import akka.http.scaladsl.server.Directives.pathEndOrSingleSlash
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`User-Agent`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import org.json4s.{Extraction, JField, JObject, JValue}
import webecho.ServiceDependencies
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.scalap.scalasig.ClassFileParser.header
import webecho.dependencies.echocache.EchoOrigin

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.UUID

case class InvalidRequest(message: String)


case class EchoRouting(dependencies: ServiceDependencies) extends Routing {

  val prefix = dependencies.config.webEcho.site.cleanedPrefix.map(p => s"/$p").getOrElse("")

  override def routes: Route = newEcho ~ getEcho ~ postEcho ~ info ~ echoInfo

  private val receivedCache = dependencies.echoCache

  def epochToUTCDateTime(epoch: Long): OffsetDateTime = {
    Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.UTC)
  }

  def info: Route = {
    get {
      path("info") {
        receivedCache.entriesInfo() match {
          case Some(info) =>
            complete(
              Map(
                "entriesCount" -> info.count,
                "lastUpdated" -> epochToUTCDateTime(info.lastUpdated)
              )
            )
          case None =>
            complete(StatusCodes.PreconditionFailed -> InvalidRequest("nothing in cache"))
        }
      }
    }
  }

  def now() = System.currentTimeMillis()

  def newEcho: Route = {
    pathEndOrSingleSlash {
      get {
        optionalHeaderValueByName("User-Agent") { userAgent =>
          extractClientIP { clientIP =>
            val uuid = UUID.randomUUID()
            val uri = Uri(s"$prefix/echoed/${uuid.toString}")
            val origin = EchoOrigin(
              createdOn = now(),
              createdByIpAddress = clientIP.toOption.map(_.getHostAddress),
              createdByUserAgent = userAgent
            )
            receivedCache.entryCreate(uuid, origin)
            redirect(uri, StatusCodes.TemporaryRedirect)
          }
        }
      }
    }
  }

  def getEcho: Route = {
    get {
      path("echoed" / JavaUUID) { uuid =>
        parameters("latest".optional, "count".as[Int].optional) { (latest, count) =>
          receivedCache.get(uuid) match {
            case None => complete(StatusCodes.Forbidden -> InvalidRequest("Well tried ;)"))
            case Some(it) if !it.hasNext => complete(StatusCodes.PreconditionFailed -> InvalidRequest("No data received yet:("))
            case Some(it) if latest.isDefined => complete(it.next())
            case Some(it) if count.isDefined && count.get >= 0 =>
              val source = Source.fromIterator(() => it.take(count.get)) // Stream the response
              complete(source)
            case Some(it) =>
              val source = Source.fromIterator(() => it) // Stream the response
              complete(source)
          }
        }
      }
    }
  }


  def echoInfo: Route = {
    get {
      path("echoed" / JavaUUID / "info") { uuid =>
        receivedCache.entryInfo(uuid) match {
          case None => complete(StatusCodes.Forbidden -> InvalidRequest("Well tried ;)"))
          case Some(info) =>
            complete {
              Map(
                "echoCount" -> info.count,
                "lastUpdated" -> epochToUTCDateTime(info.lastUpdated),
              ) ++
                info.origin.flatMap(_.createdByIpAddress).map(v => "createdByRemoteHostAddress"->v) ++
                info.origin.flatMap(_.createdByUserAgent).map(v => "createdByUserAgent"-> v) ++
                info.origin.map(_.createdOn).map(v => "createdOn"-> epochToUTCDateTime(v))
            }
        }
      }
    }
  }


  def postEcho: Route = {
    post {
      path("echoed" / JavaUUID) { uuid =>
        optionalHeaderValueByName("User-Agent") { userAgent =>
          extractClientIP { clientIP =>
            if (!receivedCache.entryExists(uuid)) {
              complete(StatusCodes.Forbidden -> InvalidRequest("Well tried ;)"))
            } else {
              entity(as[JValue]) { posted =>
                val enriched = JObject(
                  JField("data", posted),
                  JField("addedOn", Extraction.decompose(OffsetDateTime.now())),
                  JField("addedByRemoteHostAddress", Extraction.decompose(clientIP.toOption.map(_.getHostAddress))),
                  JField("addedByUserAgent", Extraction.decompose(userAgent))
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


}

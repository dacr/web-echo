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

import akka.http.scaladsl.server.Directives.pathEndOrSingleSlash
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import org.json4s.{Extraction, JField, JObject, JValue}
import webecho.ServiceDependencies
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import webecho.dependencies.echocache.EchoOrigin
import webecho.tools.DateTimeTools

import java.time.OffsetDateTime
import java.util.UUID

case class InvalidRequest(message: String)


case class EchoRouting(dependencies: ServiceDependencies) extends Routing with DateTimeTools {

  val apiURL = dependencies.config.webEcho.site.apiURL
  val meta = dependencies.config.webEcho.metaInfo
  val startedDate = now()
  val instanceUUID = UUID.randomUUID().toString

  override def routes: Route = pathPrefix("api") {
    newWebHookEcho ~ getEcho ~ postEcho ~ info ~ echoInfo
  }

  private val receivedCache = dependencies.echoCache


  def info: Route = {
    get {
      path("info") {
        receivedCache.entriesInfo() match {
          case Some(info) =>
            complete(
              Map(
                "entriesCount" -> info.count,
                "instanceUUID"->instanceUUID,
                "startedOn" -> epochToUTCDateTime(startedDate),
                "version" -> meta.version,
                "buildDate" -> meta.buildDateTime
              )
            )
          case None =>
            complete(StatusCodes.PreconditionFailed -> InvalidRequest("nothing in cache"))
        }
      }
    }
  }

  def newWebHookEcho: Route = path("webhook") {
    pathEndOrSingleSlash {
      post {
        optionalHeaderValueByName("User-Agent") { userAgent =>
          extractClientIP { clientIP =>
            val uuid = UUID.randomUUID()
            val url = s"$apiURL/echoed/$uuid"
            val origin = EchoOrigin(
              createdOn = now(),
              createdByIpAddress = clientIP.toOption.map(_.getHostAddress),
              createdByUserAgent = userAgent
            )
            receivedCache.entryCreate(uuid, origin)
            complete {
              Map(
                "uuid"->uuid,
                "url"->url
              )
            }
          }
        }
      }
    }
  }

  def getEcho: Route = {
    get {
      path("echoed" / JavaUUID) { uuid =>
        parameters("count".as[Int].optional) { count =>
          receivedCache.get(uuid) match {
            case None => complete(StatusCodes.Forbidden -> InvalidRequest("Well tried ;)"))
            case Some(it) if !it.hasNext => complete(StatusCodes.PreconditionFailed -> InvalidRequest("No data received yet:("))
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
      path("info" / JavaUUID ) { uuid =>
        receivedCache.entryInfo(uuid) match {
          case None => complete(StatusCodes.Forbidden -> InvalidRequest("Well tried ;)"))
          case Some(info) =>
            complete {
              Map(
                "echoCount" -> info.count,
                "lastUpdated" -> epochToUTCDateTime(info.lastUpdated),
              ) ++
                info.origin.flatMap(_.createdByIpAddress).map(v => "createdByRemoteHostAddress" -> v) ++
                info.origin.flatMap(_.createdByUserAgent).map(v => "createdByUserAgent" -> v) ++
                info.origin.map(_.createdOn).map(v => "createdOn" -> epochToUTCDateTime(v))
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
                complete{
                  Map(
                    "message"->"success"
                  )
                }
              }
            }
          }
        }
      }
    }
  }


}

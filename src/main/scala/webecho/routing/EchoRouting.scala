/*
 * Copyright 2020-2022 David Crosson
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

import org.apache.pekko.http.scaladsl.server.Directives.pathEndOrSingleSlash
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Source
import org.json4s.{Extraction, JField, JObject, JValue}
import webecho.ServiceDependencies
import com.github.pjfanning.pekkohttpjson4s.Json4sSupport.*
import webecho.model.OperationOrigin
import webecho.tools.{DateTimeTools, JsonImplicits, UniqueIdentifiers}

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.{Failure, Success}

case class InvalidRequest(message: String)

case class WebSocketInput(uri: String, userData: Option[String])

case class EchoRouting(dependencies: ServiceDependencies) extends Routing with DateTimeTools with JsonImplicits {

  val apiURL       = dependencies.config.webEcho.site.apiURL
  val meta         = dependencies.config.webEcho.metaInfo
  val startedDate  = now()
  val instanceUUID = UniqueIdentifiers.randomUUID().toString

  override def routes: Route = pathPrefix("api") {
    concat(
      newWebHookEcho,
      getEcho,
      postEcho,
      info,
      echoInfo,
      webSocketRegister,
      webSocketList,
      webSocketDelete,
      webSocketGet
    )
  }

  private val echoStore = dependencies.echoStore

  def info: Route = {
    path("info") {
      get {
        echoStore.echoesInfo() match {
          case Some(info) =>
            complete(
              Map(
                "entriesCount" -> info.count,
                "instanceUUID" -> instanceUUID,
                "startedOn"    -> instantToUTCDateTime(startedDate),
                "version"      -> meta.version,
                "buildDate"    -> meta.buildDateTime
              )
            )
          case None       =>
            complete(StatusCodes.PreconditionFailed -> InvalidRequest("nothing in cache"))
        }
      }
    }
  }

  def newWebHookEcho: Route = path("webhook" | "recorder") {
    pathEndOrSingleSlash {
      post {
        optionalHeaderValueByName("User-Agent") { userAgent =>
          extractClientIP { clientIP =>
            val uuid   = UniqueIdentifiers.timedUUID()
            val url    = s"$apiURL/echoed/$uuid"
            val origin = OperationOrigin(
              createdOn = now(),
              createdByIpAddress = clientIP.toOption.map(_.getHostAddress),
              createdByUserAgent = userAgent
            )
            echoStore.echoAdd(uuid, Some(origin))
            complete {
              Map(
                "uuid" -> uuid,
                "url"  -> url
              )
            }
          }
        }
      }
    }
  }

  def getEcho: Route = {
    path("echoed" / JavaUUID) { uuid =>
      get {
        parameters("count".as[Int].optional) { count =>
          echoStore.echoGet(uuid) match {
            case None                                          => complete(StatusCodes.Forbidden -> InvalidRequest("Well tried ;)"))
            case Some(it) if !it.hasNext                       => complete(StatusCodes.PreconditionFailed -> InvalidRequest("No data received yet:("))
            case Some(it) if count.isDefined && count.get >= 0 =>
              val source = Source.fromIterator(() => it.take(count.get)) // Stream the response
              complete(source)
            case Some(it)                                      =>
              val source = Source.fromIterator(() => it) // Stream the response
              complete(source)
          }
        }
      }
    }
  }

  def echoInfo: Route = {
    path("info" / JavaUUID) { uuid =>
      get {
        echoStore.echoInfo(uuid) match {
          case None       => complete(StatusCodes.Forbidden -> InvalidRequest("Well tried ;)"))
          case Some(info) =>
            complete {
              Map(
                "echoCount" -> info.count
              ) ++
                info.lastUpdated.map("lastUpdated" -> instantToUTCDateTime(_)) ++
                info.origin.flatMap(_.createdByIpAddress).map(v => "createdByRemoteHostAddress" -> v) ++
                info.origin.flatMap(_.createdByUserAgent).map(v => "createdByUserAgent" -> v) ++
                info.origin.map(_.createdOn).map(v => "createdOn" -> instantToUTCDateTime(v))
            }
        }
      }
    }
  }

  def postEcho: Route = {
    path("echoed" / JavaUUID) { uuid =>
      post {
        optionalHeaderValueByName("User-Agent") { userAgent =>
          extractClientIP { clientIP =>
            if (!echoStore.echoExists(uuid)) {
              complete(StatusCodes.Forbidden -> InvalidRequest("Well tried ;)"))
            } else {
              entity(as[JValue]) { posted =>
                val enriched = JObject(
                  JField("data", posted),
                  JField("addedOn", Extraction.decompose(OffsetDateTime.now())),
                  JField("addedByRemoteHostAddress", Extraction.decompose(clientIP.toOption.map(_.getHostAddress))),
                  JField("addedByUserAgent", Extraction.decompose(userAgent))
                )
                echoStore.echoAddValue(uuid, enriched) match
                  case Failure(exception) =>
                    complete {
                      Map(
                        "message" -> "failure"
                      )
                    }
                  case Success(meta)      =>
                    complete {
                      Map(
                        "message" -> "success",
                        "meta"    -> Map(
                          "sha256"    -> meta.sha256,
                          "index"     -> meta.index,
                          "timestamp" -> meta.timestamp
                        )
                      )
                    }
              }
            }
          }
        }
      }
    }
  }

  def webSocketList: Route = {
    path("echoed" / JavaUUID / "websocket") { entryUUID =>
      get {
        onSuccess(dependencies.webSocketsBot.webSocketList(entryUUID)) {
          case Some(result) =>
            complete {
              result.map { ob =>
                Map(
                  "uri"      -> ob.uri,
                  "userInfo" -> ob.userData,
                  "uuid"     -> ob.uuid
                )
              }
            }
          case None         => complete(StatusCodes.NotFound -> "Unknown UUID")
        }
      }
    }
  }

  def webSocketRegister: Route = {
    path("echoed" / JavaUUID / "websocket") { entryUUID =>
      post {
        entity(as[WebSocketInput]) { input =>
          optionalHeaderValueByName("User-Agent") { userAgent =>
            extractClientIP { clientIP =>
              val origin = OperationOrigin(
                createdOn = now(),
                createdByIpAddress = clientIP.toOption.map(_.getHostAddress),
                createdByUserAgent = userAgent
              )
              onSuccess(dependencies.webSocketsBot.webSocketAdd(entryUUID, input.uri, input.userData, Some(origin))) { result =>
                complete {
                  Map(
                    "uri"      -> result.uri,
                    "userInfo" -> result.userData,
                    "uuid"     -> result.uuid
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  def webSocketGet: Route = {
    path("echoed" / JavaUUID / "websocket" / JavaUUID) { (entryUUID, uuid) =>
      get {
        onSuccess(dependencies.webSocketsBot.webSocketGet(entryUUID, uuid)) {
          case Some(result) =>
            complete {
              Map(
                "uri"      -> result.uri,
                "userInfo" -> result.userData,
                "uuid"     -> result.uuid
              )
            }
          case None         => complete(StatusCodes.NotFound -> "Unknown UUID")
        }
      }
    }
  }

  def webSocketDelete: Route = {
    path("echoed" / JavaUUID / "websocket" / JavaUUID) { (entryUUID, uuid) =>
      delete {
        onSuccess(dependencies.webSocketsBot.webSocketDelete(entryUUID, uuid)) {
          case Some(true)  => complete(StatusCodes.OK -> "Success")
          case Some(false) => complete(StatusCodes.InternalServerError -> s"Unable to delete $entryUUID/$uuid")
          case None        => complete(StatusCodes.NotFound -> "Unknown UUID")
        }
      }
    }
  }

  def webSocketAlive: Route = { // TODO update swagger.json
    path("echoed" / JavaUUID / "websocket" / JavaUUID / "health") { (entryUUID, uuid) =>
      get {
        onSuccess(dependencies.webSocketsBot.webSocketAlive(entryUUID, uuid)) {
          case Some(true)  => complete(StatusCodes.OK -> "Success")
          case Some(false) => complete(StatusCodes.InternalServerError -> s"Unable to connect to web socket for $entryUUID/$uuid")
          case None        => complete(StatusCodes.NotFound -> "Unknown UUID")
        }
      }
    }
  }

}

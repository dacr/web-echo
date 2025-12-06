package webecho.routing

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.model.StatusCode
import webecho.ServiceDependencies
import webecho.model.{EchoInfo, EchoWebSocket, EchoAddedMeta, OperationOrigin} // Import domain models
import webecho.apimodel.* // Import Api DTOs
import webecho.tools.{DateTimeTools, JsonImplicits, UniqueIdentifiers}
import webecho.routing.TapirEndpoints.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import org.json4s.{Extraction, JField, JObject, JValue}
import java.time.OffsetDateTime
import java.util.UUID
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.*

case class TapirRoutes(dependencies: ServiceDependencies) extends DateTimeTools with JsonImplicits {
  
  private val echoStore = dependencies.echoStore
  private val config = dependencies.config.webEcho
  private val apiURL = config.site.apiURL
  private val meta = config.metaInfo
  private val startedDate = now()
  private val instanceUUID = UniqueIdentifiers.randomUUID().toString

  // Define implicit transformer for EchoWebSocket to ApiWebSocket
  implicit val echoWebSocketToApiWebSocketTransformer: Transformer[EchoWebSocket, ApiWebSocket] =
    Transformer.define[EchoWebSocket, ApiWebSocket]
      .withFieldComputed(_.userInfo, src => src.userData)
      .buildTransformer


  private val infoLogic = infoEndpoint.serverLogic { _ =>
    echoStore.echoesInfo() match {
      case Some(info) =>
        Future.successful(Right(ApiServiceInfo(
          entriesCount = info.count,
          instanceUUID = instanceUUID,
          startedOn = instantToUTCDateTime(startedDate),
          version = meta.version,
          buildDate = meta.buildDateTime
        )))
      case None =>
        Future.successful(Left(ApiErrorMessage("nothing in cache")))
    }
  }

  private val infoUuidLogic = infoUuidEndpoint.serverLogic { uuid =>
    echoStore.echoInfo(uuid) match {
      case Some(info: EchoInfo) =>
        val apiRecorderInfoDetail = info.into[ApiRecorderInfoDetail]
          .withFieldConst(_.echoCount, info.count)
          .withFieldConst(_.lastUpdated, info.lastUpdated.map(instantToUTCDateTime))
          .withFieldConst(_.createdByRemoteHostAddress, info.origin.flatMap(_.createdByIpAddress))
          .withFieldConst(_.createdByUserAgent, info.origin.flatMap(_.createdByUserAgent))
          .withFieldConst(_.createdOn, info.origin.map(_.createdOn).map(instantToUTCDateTime))
          .transform
        Future.successful(Right(apiRecorderInfoDetail))
      case None =>
        Future.successful(Left(ApiErrorMessage("Well tried ;)")))
    }
  }

  private def createRecorderLogic(userAgent: Option[String], clientIP: Option[String]) = {
    val uuid = UniqueIdentifiers.timedUUID()
    val url = s"$apiURL/echoed/$uuid"
    val origin = OperationOrigin(
      createdOn = now(),
      createdByIpAddress = clientIP,
      createdByUserAgent = userAgent
    )
    echoStore.echoAdd(uuid, Some(origin))
    Future.successful(Right(ApiRecorderCreationResult(uuid, url)))
  }

  private val createRecorderServerLogic = createRecorderEndpoint.serverLogic { case (userAgent, clientIP) =>
    createRecorderLogic(userAgent, clientIP)
  }

  private val createWebhookServerLogic = createWebhookEndpoint.serverLogic { case (userAgent, clientIP) =>
    createRecorderLogic(userAgent, clientIP)
  }

  private val getEchoLogic = getEchoEndpoint.serverLogic { case (uuid, count) =>
    Future {
      echoStore.echoGet(uuid) match {
        case None => Left((StatusCode.Forbidden, ApiErrorMessage("Well tried ;)")))
        case Some(it) if !it.hasNext => Left((StatusCode.PreconditionFailed, ApiErrorMessage("No data received yet:(")))
        case Some(it) =>
          val finalIt = if (count.exists(_ >= 0)) it.take(count.get) else it
          val source = Source.fromIterator(() => finalIt)
            .map(json => ByteString(chosenSerialization.write(json)))
            .intersperse(ByteString("["), ByteString(","), ByteString("]"))
          Right(source)
      }
    }
  }

  private val postEchoLogic = postEchoEndpoint.serverLogic { case (uuid, body, userAgent, clientIP) =>
    if (!echoStore.echoExists(uuid)) {
      Future.successful(Left(ApiErrorMessage("Well tried ;)")))
    } else {
      val enriched = JObject(
        JField("data", body),
        JField("addedOn", Extraction.decompose(OffsetDateTime.now())),
        JField("addedByRemoteHostAddress", Extraction.decompose(clientIP)),
        JField("addedByUserAgent", Extraction.decompose(userAgent))
      )
      echoStore.echoAddValue(uuid, enriched) match {
        case Failure(_) =>
           Future.successful(Right(ApiPostEchoResult("failure")))
        case Success(meta: EchoAddedMeta) =>
          val apiPostEchoMeta = meta.into[ApiPostEchoMeta].transform
          Future.successful(Right(ApiPostEchoResult("success", Some(apiPostEchoMeta))))
      }
    }
  }

  private val webSocketListLogic = webSocketListEndpoint.serverLogic { uuid =>
    dependencies.webSocketsBot.webSocketList(uuid).flatMap {
      case Some(result) =>
        Future.successful(Right(result.map(ob => ob.transformInto[ApiWebSocket]).toList))
      case None =>
        Future.successful(Left("Unknown UUID"))
    }
  }

  private val webSocketRegisterLogic = webSocketRegisterEndpoint.serverLogic { case (uuid, input: ApiWebSocketInput, userAgent, clientIP) =>
    val origin = OperationOrigin(
      createdOn = now(),
      createdByIpAddress = clientIP,
      createdByUserAgent = userAgent
    )
    dependencies.webSocketsBot.webSocketAdd(uuid, input.uri, input.userData, Some(origin)).map { result =>
       Right(result.transformInto[ApiWebSocket])
    }
  }

  private val webSocketGetLogic = webSocketGetEndpoint.serverLogic { case (uuid, wsuuid) =>
     dependencies.webSocketsBot.webSocketGet(uuid, wsuuid).flatMap {
       case Some(result: EchoWebSocket) =>
          Future.successful(Right(result.transformInto[ApiWebSocket]))
       case None =>
          Future.successful(Left("Unknown UUID"))
     }
  }

  private val webSocketDeleteLogic = webSocketDeleteEndpoint.serverLogic { case (uuid, wsuuid) =>
     dependencies.webSocketsBot.webSocketDelete(uuid, wsuuid).map {
       case Some(true) => Right("Success")
       case Some(false) => Left((StatusCode.InternalServerError, s"Unable to delete $uuid/$wsuuid"))
       case None => Left((StatusCode.NotFound, "Unknown UUID"))
     }
  }

  private val webSocketHealthLogic = webSocketHealthEndpoint.serverLogic { case (uuid, wsuuid) =>
      dependencies.webSocketsBot.webSocketAlive(uuid, wsuuid).map {
       case Some(true) => Right("Success")
       case Some(false) => Left((StatusCode.InternalServerError, s"Unable to connect to web socket for $uuid/$wsuuid"))
       case None => Left((StatusCode.NotFound, "Unknown UUID"))
     }
  }

  private val healthLogic = healthEndpoint.serverLogic { _ =>
     Future.successful(Right(ApiHealth()))
  }

  val allEndpoints = List(
    infoEndpoint,
    infoUuidEndpoint,
    createRecorderEndpoint,
    createWebhookEndpoint,
    getEchoEndpoint,
    postEchoEndpoint,
    webSocketListEndpoint,
    webSocketRegisterEndpoint,
    webSocketGetEndpoint,
    webSocketDeleteEndpoint,
    webSocketHealthEndpoint,
    healthEndpoint
  )

  val swaggerEndpoints = SwaggerInterpreter().fromEndpoints[Future](allEndpoints, "Web Echo API", "1.3.0")

  val routes: Route = PekkoHttpServerInterpreter().toRoute(
    List(
      infoLogic,
      infoUuidLogic,
      createRecorderServerLogic,
      createWebhookServerLogic,
      getEchoLogic,
      postEchoLogic,
      webSocketListLogic,
      webSocketRegisterLogic,
      webSocketGetLogic,
      webSocketDeleteLogic,
      webSocketHealthLogic,
      healthLogic
    ) ++ swaggerEndpoints
  )
}
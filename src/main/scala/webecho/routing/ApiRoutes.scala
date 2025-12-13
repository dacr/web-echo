package webecho.routing

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.apispec.openapi.Server
import sttp.model.StatusCode
import webecho.ServiceDependencies
import webecho.model.{EchoAddedMeta, EchoInfo, EchoWebSocket, Origin}
import webecho.apimodel.*
import webecho.tools.{DateTimeTools, JsonSupport, UniqueIdentifiers}
import webecho.routing.ApiEndpoints.*

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.*

case class ApiRoutes(dependencies: ServiceDependencies) extends DateTimeTools with JsonSupport {

  private val echoStore    = dependencies.echoStore
  private val config       = dependencies.config.webEcho
  private val apiURL       = config.site.apiURL
  private val meta         = config.metaInfo
  private val startedDate  = now()

  // Define implicit transformer for EchoWebSocket to ApiWebSocket
  implicit val echoWebSocketToApiWebSocketTransformer: Transformer[EchoWebSocket, ApiWebSocket] =
    Transformer
      .define[EchoWebSocket, ApiWebSocket]
      .withFieldComputed(_.userData, src => src.userData)
      .buildTransformer

  private def createRecorderLogic(userAgent: Option[String], clientIP: Option[String]) = {
    val uuid   = UniqueIdentifiers.timedUUID()
    val url    = s"$apiURL/recorder/$uuid"
    val origin = Origin(
      createdOn = OffsetDateTime.now(),
      createdByIpAddress = clientIP,
      createdByUserAgent = userAgent
    )
    echoStore.echoAdd(uuid, Some(origin))
    Future.successful(
      Right(
        ApiRecorder(
          id = uuid,
          dataTargetURL = url,
          origin = Some(origin.transformInto[ApiOrigin]),
          lastUpdated = None,
          recordsCount = None
        )
      )
    )
  }

  private val recorderCreateLogic = recorderCreateEndpoint.serverLogic { case (userAgent, clientIP) =>
    createRecorderLogic(userAgent, clientIP)
  }

  private val recorderGetLogic = recorderGetEndpoint.serverLogic { uuid =>
    echoStore.echoInfo(uuid) match {
      case Some(info: EchoInfo) =>
        val url      = s"$apiURL/recorder/$uuid"
        val recorder =
          info
            .into[ApiRecorder]
            .withFieldConst(_.id, uuid)
            .withFieldConst(_.recordsCount, Some(info.count))
            .withFieldConst(_.dataTargetURL, url)
            .withFieldConst(_.lastUpdated, info.lastUpdated.map(_.atOffset(ZoneOffset.UTC)))
            .transform

        Future.successful(Right(recorder))
      case None                 =>
        Future.successful(Left(ApiErrorMessage("Well tried ;)")))
    }
  }

  private val recorderGetRecordsLogic = recorderGetRecordsEndpoint.serverLogic { case (uuid, count) =>
    Future {
      echoStore.echoGet(uuid) match {
        case None                    => Left((StatusCode.Forbidden, ApiErrorMessage("Well tried ;)")))
        case Some(it) if !it.hasNext => Left((StatusCode.PreconditionFailed, ApiErrorMessage("No data received yet:(")))
        case Some(it)                =>
          val finalIt = if (count.exists(_ >= 0)) it.take(count.get) else it
          val source  = Source
            .fromIterator(() => finalIt)
            .map(jsonString => ByteString(jsonString))
            .intersperse(ByteString("["), ByteString(","), ByteString("]"))
          Right(source)
      }
    }
  }

  private val recorderReceiveDataLogic = recorderReceiveDataEndpoint.serverLogic { case (uuid, body, userAgent, clientIP) =>
    if (!echoStore.echoExists(uuid)) {
      Future.successful(Left( (StatusCode.Forbidden, ApiErrorMessage("Well tried ;)"))))
    } else {
      val enriched = Map(
        "data" -> body,
        "addedOn" -> OffsetDateTime.now().toString,
        "addedByRemoteHostAddress" -> clientIP,
        "addedByUserAgent" -> userAgent
      )
      echoStore.echoAddValue(uuid, enriched) match {
        case Failure(error)                   =>
          Future.successful(Left( (StatusCode.InternalServerError, ApiErrorMessage("Internal issue"))))
        case Success(meta: EchoAddedMeta) =>
          val proof = meta.into[ApiReceiptProof].transform
          Future.successful(Right(proof))
      }
    }
  }

  private val recorderListAttachedWebsocketsLogic = recorderListAttachedWebsocketsEndpoint.serverLogic { uuid =>
    dependencies.webSocketsBot.webSocketList(uuid).flatMap {
      case Some(result) =>
        Future.successful(Right(result.map(ob => ob.transformInto[ApiWebSocket]).toList))
      case None         =>
        Future.successful(Left("Unknown UUID"))
    }
  }

  private val recorderRegisterWebsocketLogic = recorderRegisterWebsocketEndpoint.serverLogic { case (uuid, input: ApiWebSocketSpec, userAgent, clientIP) =>
    val origin = Origin(
      createdOn = OffsetDateTime.now(),
      createdByIpAddress = clientIP,
      createdByUserAgent = userAgent
    )
    dependencies.webSocketsBot.webSocketAdd(uuid, input.uri, input.userData, Some(origin)).map { result =>
      Right(result.transformInto[ApiWebSocket])
    }
  }

  private val recorderGetWebsocketInfoLogic = recorderGetWebsocketInfoEndpoint.serverLogic { case (uuid, wsuuid) =>
    dependencies.webSocketsBot.webSocketGet(uuid, wsuuid).flatMap {
      case Some(result: EchoWebSocket) =>
        Future.successful(Right(result.transformInto[ApiWebSocket]))
      case None                        =>
        Future.successful(Left("Unknown UUID"))
    }
  }

  private val recorderUnregisterWebsocketLogic = recorderUnregisterWebsocketEndpoint.serverLogic { case (uuid, wsuuid) =>
    dependencies.webSocketsBot.webSocketDelete(uuid, wsuuid).map {
      case Some(true)  => Right("Success")
      case Some(false) => Left((StatusCode.InternalServerError, s"Unable to delete $uuid/$wsuuid"))
      case None        => Left((StatusCode.NotFound, "Unknown UUID"))
    }
  }

  private val recorderCheckWebsocketStateLogic = recorderCheckWebsocketStateEndpoint.serverLogic { case (uuid, wsuuid) =>
    dependencies.webSocketsBot.webSocketAlive(uuid, wsuuid).map {
      case Some(true)  => Right("Success")
      case Some(false) => Left((StatusCode.InternalServerError, s"Unable to connect to web socket for $uuid/$wsuuid"))
      case None        => Left((StatusCode.NotFound, "Unknown UUID"))
    }
  }

  private val systemServiceInfoLogic = systemServiceInfoEndpoint.serverLogic { _ =>
    echoStore.echoesInfo() match {
      case Some(info) =>
        Future.successful(
          Right(
            ApiServiceInfo(
              recordersCount = info.count,
              startedOn = instantToUTCDateTime(startedDate),
              version = meta.version,
              buildDate = meta.buildDateTime
            )
          )
        )
      case None       =>
        Future.successful(Left(ApiErrorMessage("nothing in cache")))
    }
  }

  private val systemHealthLogic = systemHealthEndpoint.serverLogic { _ =>
    Future.successful(Right(ApiHealth()))
  }

  val allEndpoints = List(
    recorderCreateEndpoint,
    recorderReceiveDataEndpoint,
    recorderGetEndpoint,
    recorderGetRecordsEndpoint,
    recorderListAttachedWebsocketsEndpoint,
    recorderRegisterWebsocketEndpoint,
    recorderGetWebsocketInfoEndpoint,
    recorderUnregisterWebsocketEndpoint,
    recorderCheckWebsocketStateEndpoint,
    systemServiceInfoEndpoint,
    systemHealthEndpoint
  )

  val apiDocumentationEndpoints = SwaggerInterpreter(
    customiseDocsModel = _.addServer(Server(apiURL))
  ).fromEndpoints[Future](allEndpoints, "Web Echo API", "2.0")

  val routes: Route = concat(
    pathPrefix(separateOnSlashes(config.site.apiSuffix.stripPrefix("/"))) {
      PekkoHttpServerInterpreter().toRoute(
        List(
          recorderGetLogic,
          recorderCreateLogic,
          recorderGetRecordsLogic,
          recorderReceiveDataLogic,
          recorderListAttachedWebsocketsLogic,
          recorderRegisterWebsocketLogic,
          recorderGetWebsocketInfoLogic,
          recorderUnregisterWebsocketLogic,
          recorderCheckWebsocketStateLogic,
          systemServiceInfoLogic,
          systemHealthLogic
        )
      )
    },
    PekkoHttpServerInterpreter().toRoute(apiDocumentationEndpoints)
  )
}


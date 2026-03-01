package webecho.routing

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.NotUsed
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.apispec.openapi.Server
import sttp.model.StatusCode
import webecho.ServiceDependencies
import webecho.model.{ReceiptProof, EchoInfo, WebSocket, Origin, Record, Webhook}
import webecho.apimodel.*
import webecho.tools.{DateTimeTools, JsonSupport, UniqueIdentifiers, NetworkTools}
import webecho.routing.ApiEndpoints.*
import webecho.logic._
import webecho.security.UserProfile

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import webecho.tools.JsonSupport.apiRecordCodec

case class ApiRoutes(dependencies: ServiceDependencies) extends DateTimeTools {

  private val logic       = dependencies.logic
  private val config      = dependencies.config.webEcho
  private val apiURL      = config.site.apiURL
  private val meta        = config.metaInfo
  private val startedDate = now()

  // Define implicit transformer for EchoWebSocket to ApiWebSocket
  implicit val echoWebSocketToApiWebSocketTransformer: Transformer[WebSocket, ApiWebSocket] =
    Transformer
      .define[WebSocket, ApiWebSocket]
      .withFieldComputed(_.userData, src => src.userData)
      .buildTransformer

  private def mapError(error: LogicError): ApiError = error match {
    case RecorderNotFound(_) => ApiErrorNotFound("Unknown UUID")
    case AccountPending      => ApiErrorForbidden("Account pending validation. Please come back later.")
    case AccessDenied(msg)   => ApiErrorForbidden(msg)
    case InvalidInput(msg)   => ApiErrorBadRequest(msg)
    case SystemError(msg)    => ApiErrorInternalIssue(msg)
  }

  private val validateToken: Option[String] => Future[Either[ApiError, Option[UserProfile]]] = {
    case Some(t) =>
      dependencies.securityService.validate(t).map {
        case Right(profile) => Right(Some(profile))
        case Left(msg)      => Left(ApiErrorForbidden(msg))
      }
    case None    =>
      Future.successful(Right(None))
  }

  private val recorderCreateLogic =
    recorderCreateEndpoint
      .serverSecurityLogic(validateToken)
      .serverLogic { profile => inputs =>
        val (userAgent, clientIP)        = inputs
        implicit val ctx: CommandContext = CommandContext(profile, clientIP, userAgent)

        logic.createRecorder.map {
          case Right(recorder) => Right(recorder)
          case Left(err)       => Left(mapError(err))
        }
      }

  private val recorderUpdateLogic =
    recorderUpdateEndpoint
      .serverSecurityLogic(validateToken)
      .serverLogic { profile =>
        { case (uuid, update) =>
          import scala.concurrent.duration.{Duration, FiniteDuration}
          val lifeExpectancyDur            = update.lifeExpectancy.collect { case fd: FiniteDuration => fd }
          implicit val ctx: CommandContext = CommandContext(profile, None, None) // IP/UA not captured in update input

          logic.updateRecorder(uuid, update.description, lifeExpectancyDur).flatMap {
            case Left(err) => Future.successful(Left(mapError(err)))
            case Right(_)  =>
              logic.getRecorder(uuid).map {
                case Right(info) =>
                  val url      = s"$apiURL/record/$uuid"
                  val fetchUrl = s"$apiURL/recorder/$uuid/records"
                  val recorder =
                    info
                      .into[ApiRecorder]
                      .withFieldConst(_.id, uuid)
                      .withFieldConst(_.recordsCount, Some(info.count))
                      .withFieldConst(_.sendDataURL, url)
                      .withFieldConst(_.fetchDataURL, fetchUrl)
                      .withFieldConst(_.lifeExpectancy, info.lifeExpectancy)
                      .withFieldConst(_.updatedOn, info.updatedOn.map(_.atOffset(ZoneOffset.UTC)))
                      .transform
                  Right(recorder)
                case Left(err)   =>
                  Left(mapError(err))
              }
          }
        }
      }

  private val recorderGetLogic =
    recorderGetEndpoint.serverLogic { uuid =>
      // Read operations are public, so empty context
      implicit val ctx: CommandContext = CommandContext(None, None, None)
      logic.getRecorder(uuid).map {
        case Right(info) =>
          val url      = s"$apiURL/record/$uuid"
          val fetchUrl = s"$apiURL/recorder/$uuid/records"
          val recorder =
            info
              .into[ApiRecorder]
              .withFieldConst(_.id, uuid)
              .withFieldConst(_.recordsCount, Some(info.count))
              .withFieldConst(_.sendDataURL, url)
              .withFieldConst(_.fetchDataURL, fetchUrl)
              .withFieldConst(_.lifeExpectancy, info.lifeExpectancy)
              .withFieldConst(_.updatedOn, info.updatedOn.map(_.atOffset(ZoneOffset.UTC)))
              .transform

          Right(recorder)
        case Left(err)   =>
          Left(mapError(err))
      }
    }

  private val recorderGetRecordsLogic =
    recorderGetRecordsEndpoint.serverLogic { case (uuid, count) =>
      implicit val ctx: CommandContext = CommandContext(None, None, None)
      logic.getRecords(uuid, count).map {
        case Left(err) => Left(mapError(err))
        case Right(it) =>
          if (!it.hasNext) {
            Left(ApiErrorPreconditionFailed("No data received yet:("))
          } else {
            val source = Source
              .fromIterator(() => it)
              .map { case (proof, record) =>
                val apiProof = proof.into[ApiReceiptProof].transform

                val apiRecord = record
                  .into[ApiRecord]
                  .withFieldConst(_.receiptProof, Some(apiProof))
                  .transform

                ByteString(writeToString(apiRecord)(using apiRecordCodec) + "\n")
              }
            Right(source)
          }
      }
    }

  private val recordReceiveDataLogicFunction: ((UUID, Any, Option[String], Option[String])) => Future[Either[ApiError, ApiReceiptProof]] = { case (uuid, content, userAgent, clientIP) =>
    implicit val ctx: CommandContext = CommandContext(None, clientIP, userAgent)
    logic.addContent(uuid, content).map {
      case Left(err)    => Left(mapError(err))
      case Right(proof) => Right(proof.into[ApiReceiptProof].transform)
    }
  }

  private val recordReceiveDataGetLogic =
    recordReceiveDataGetEndpoint.serverLogic { (uuid, queryParams, userAgent, clientIP) =>
      val content = queryParams.toMap
      recordReceiveDataLogicFunction(uuid, content, userAgent, clientIP)
    }

  private val recordReceiveDataPutLogic =
    recordReceiveDataPutEndpoint.serverLogic { (uuid, content, userAgent, clientIP) =>
      recordReceiveDataLogicFunction(uuid, content, userAgent, clientIP)
    }

  private val recordReceiveDataPostLogic =
    recordReceiveDataPostEndpoint.serverLogic { (uuid, content, userAgent, clientIP) =>
      recordReceiveDataLogicFunction(uuid, content, userAgent, clientIP)
    }

  private val recorderListAttachedWebsocketsLogic =
    recorderListAttachedWebsocketsEndpoint.serverLogic { uuid =>
      implicit val ctx: CommandContext = CommandContext(None, None, None)
      logic.listWebSockets(uuid).map {
        case Right(result) =>
          Right(result.map(ob => ob.transformInto[ApiWebSocket]).toList)
        case Left(err)     =>
          Left(mapError(err))
      }
    }

  private val recorderRegisterWebsocketLogic =
    recorderRegisterWebsocketEndpoint.serverLogic { case (uuid, input: ApiWebSocketSpec, userAgent, clientIP) =>
      implicit val ctx: CommandContext = CommandContext(None, clientIP, userAgent)
      logic.registerWebSocket(uuid, input.uri, input.userData, input.expire).map {
        case Left(err) => Left(mapError(err))
        case Right(ws) => Right(ws.transformInto[ApiWebSocket])
      }
    }

  private val recorderGetWebsocketInfoLogic =
    recorderGetWebsocketInfoEndpoint.serverLogic { case (uuid, wsuuid) =>
      implicit val ctx: CommandContext = CommandContext(None, None, None)
      logic.getWebSocketInfo(uuid, wsuuid).map {
        case Right(result) =>
          Right(result.transformInto[ApiWebSocket])
        case Left(err)     =>
          Left(mapError(err))
      }
    }

  private val recorderUnregisterWebsocketLogic =
    recorderUnregisterWebsocketEndpoint.serverLogic { case (uuid, wsuuid) =>
      implicit val ctx: CommandContext = CommandContext(None, None, None)
      logic.unregisterWebSocket(uuid, wsuuid).map {
        case Right(_)  => Right("Success")
        case Left(err) => Left(mapError(err))
      }
    }

  private val recorderCheckWebsocketStateLogic =
    recorderCheckWebsocketStateEndpoint.serverLogic { case (uuid, wsuuid) =>
      implicit val ctx: CommandContext = CommandContext(None, None, None)
      logic.checkWebSocketState(uuid, wsuuid).map {
        case Right(_)  => Right("Success")
        case Left(err) => Left(mapError(err))
      }
    }

  private val systemServiceInfoLogic =
    systemServiceInfoEndpoint.serverLogic { _ =>
      implicit val ctx: CommandContext = CommandContext(None, None, None)
      logic.getServiceInfo().map {
        case Right(info) =>
          Right(
            ApiServiceInfo(
              recordersCount = info.count,
              startedOn = instantToUTCDateTime(startedDate),
              version = meta.version,
              buildDate = meta.buildDateTime
            )
          )
        case Left(err)   =>
          Left(ApiErrorPreconditionFailed(err.toString))
      }
    }

  private val systemHealthLogic =
    systemHealthEndpoint.serverLogic { _ =>
      Future.successful(Right(ApiHealth()))
    }

  val apiDocumentationEndpoints =
    SwaggerInterpreter(
      customiseDocsModel = _.addServer(Server(apiURL))
    ).fromEndpoints[Future](ApiEndpoints.all, "Web Echo API", "2.0")

  val routes: Route = concat(
    pathPrefix(separateOnSlashes(config.site.apiSuffix.stripPrefix("/"))) {
      PekkoHttpServerInterpreter().toRoute(
        List(
          recorderGetLogic,
          recorderCreateLogic,
          recorderUpdateLogic,
          recorderGetRecordsLogic,
          recorderListAttachedWebsocketsLogic,
          recorderRegisterWebsocketLogic,
          recorderGetWebsocketInfoLogic,
          recorderUnregisterWebsocketLogic,
          recorderCheckWebsocketStateLogic,
          recordReceiveDataGetLogic,
          recordReceiveDataPutLogic,
          recordReceiveDataPostLogic,
          systemServiceInfoLogic,
          systemHealthLogic
        )
      )
    },
    PekkoHttpServerInterpreter().toRoute(apiDocumentationEndpoints)
  )
}

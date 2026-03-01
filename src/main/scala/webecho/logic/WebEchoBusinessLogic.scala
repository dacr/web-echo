package webecho.logic

import webecho.ServiceDependencies
import webecho.model.{EchoInfo, Origin, ReceiptProof, Webhook, Record}
import webecho.apimodel.{ApiRecorder, ApiOrigin}
import webecho.tools.UniqueIdentifiers
import webecho.dependencies.websocketsbot.WebSocketsBot
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration.{Duration, FiniteDuration}
import webecho.tools.NetworkTools
import io.scalaland.chimney.dsl._

class WebEchoBusinessLogic(dependencies: ServiceDependencies)(implicit ec: ExecutionContext) {

  private val echoStore = dependencies.echoStore
  private val config    = dependencies.config.webEcho
  private val security  = dependencies.config.webEcho.security

  def checkAccess(implicit ctx: CommandContext): Either[LogicError, Unit] = {
    ctx.user match {
      case Some(u) if u.isPending            => Left(AccountPending)
      case None if security.keycloak.enabled => Left(AccessDenied("Authentication required"))
      case _                                 => Right(())
    }
  }

  private def withAccess[T](block: => Future[Either[LogicError, T]])(implicit ctx: CommandContext): Future[Either[LogicError, T]] = {
    checkAccess match {
      case Left(error) => Future.successful(Left(error))
      case Right(_)    => block
    }
  }

  def createRecorder(implicit ctx: CommandContext): Future[Either[LogicError, ApiRecorder]] = withAccess {
    val uuid           = UniqueIdentifiers.randomUUID()
    val now            = OffsetDateTime.now()
    val origin         = Origin(
      createdOn = now,
      createdByIpAddress = ctx.ip,
      createdByUserAgent = ctx.userAgent
    )
    val lifeExpectancy = Some(config.behavior.defaultLifeExpectancy)
    echoStore.echoAdd(id = uuid, description = None, origin = Some(origin), lifeExpectancy = lifeExpectancy)

    val apiURL           = config.site.apiURL
    val recorderUrl      = s"$apiURL/record/$uuid"
    val recorderFetchUrl = s"$apiURL/recorder/$uuid/records"

    Future.successful(
      Right(
        ApiRecorder(
          id = uuid,
          description = None,
          lifeExpectancy = lifeExpectancy,
          sendDataURL = recorderUrl,
          fetchDataURL = recorderFetchUrl,
          origin = Some(origin.transformInto[ApiOrigin]),
          updatedOn = None,
          recordsCount = Some(0)
        )
      )
    )
  }

  def getRecorder(uuid: UUID)(implicit ctx: CommandContext): Future[Either[LogicError, EchoInfo]] = {
    // Read access is currently open, but we wrap it for consistency
    echoStore.echoInfo(uuid) match {
      case Some(info) => Future.successful(Right(info))
      case None       => Future.successful(Left(RecorderNotFound(uuid)))
    }
  }

  def updateRecorder(uuid: UUID, description: Option[String], lifeExpectancy: Option[FiniteDuration])(implicit ctx: CommandContext): Future[Either[LogicError, Boolean]] = withAccess {
    if (!echoStore.echoExists(uuid)) {
      Future.successful(Left(RecorderNotFound(uuid)))
    } else {
      echoStore.echoUpdate(uuid, description, lifeExpectancy)
      Future.successful(Right(true))
    }
  }

  def getRecords(uuid: UUID, limit: Option[Int])(implicit ctx: CommandContext): Future[Either[LogicError, Iterator[(ReceiptProof, Record)]]] = {
    Future {
      echoStore.echoGetWithProof(uuid) match {
        case None     => Left(RecorderNotFound(uuid))
        case Some(it) =>
          val finalIt = if (limit.exists(_ >= 0)) it.take(limit.get) else it
          Right(finalIt)
      }
    }
  }

  def addContent(uuid: UUID, content: Any)(implicit ctx: CommandContext): Future[Either[LogicError, ReceiptProof]] = {
    if (!echoStore.echoExists(uuid)) {
      Future.successful(Left(RecorderNotFound(uuid)))
    } else {
      val enriched = Map(
        "data"    -> content,
        "addedOn" -> OffsetDateTime.now().toString,
        "webhook" -> Webhook(ctx.ip, ctx.userAgent)
      )
      echoStore.echoAddContent(uuid, enriched) match {
        case Failure(error)              => Future.successful(Left(SystemError(error.getMessage)))
        case Success(meta: ReceiptProof) => Future.successful(Right(meta))
      }
    }
  }

  def listWebSockets(uuid: UUID)(implicit ctx: CommandContext): Future[Either[LogicError, Iterable[webecho.model.WebSocket]]] = {
    dependencies.webSocketsBot.webSocketList(uuid).flatMap {
      case Some(result) => Future.successful(Right(result))
      case None         => Future.successful(Left(RecorderNotFound(uuid)))
    }
  }

  def registerWebSocket(uuid: UUID, uri: String, userData: Option[String], expireStr: Option[String])(implicit ctx: CommandContext): Future[Either[LogicError, webecho.model.WebSocket]] = {
    if (!echoStore.echoExists(uuid)) {
      Future.successful(Left(RecorderNotFound(uuid)))
    } else {
      val validationResult = if (config.security.ssrfProtectionEnabled) {
        NetworkTools.validateWebSocketUri(uri)
      } else {
        Right(java.net.URI.create(uri))
      }

      validationResult match {
        case Left(error) =>
          Future.successful(Left(InvalidInput(error)))
        case Right(_)    =>
          val origin = Origin(
            createdOn = OffsetDateTime.now(),
            createdByIpAddress = ctx.ip,
            createdByUserAgent = ctx.userAgent
          )

          val defaultDuration = config.behavior.websocketsDefaultDuration match {
            case d: FiniteDuration => d
            case _                 => Duration.create(15, "minutes")
          }

          val maxDuration = config.behavior.websocketsMaxDuration match {
            case d: FiniteDuration => d
            case _                 => Duration.create(4, "hours")
          }

          val requestedDuration = expireStr
            .flatMap { s =>
              scala.util.Try(Duration(s)).toOption
            }
            .collect { case d: FiniteDuration =>
              d
            }
            .getOrElse(defaultDuration)

          val actualDuration = if (requestedDuration > maxDuration) maxDuration else requestedDuration
          val expiresAt      = Some(OffsetDateTime.now().plusNanos(actualDuration.toNanos))

          dependencies.webSocketsBot.webSocketAdd(uuid, uri, userData, Some(origin), expiresAt).map(Right(_))
      }
    }
  }

  def getWebSocketInfo(uuid: UUID, wsUuid: UUID)(implicit ctx: CommandContext): Future[Either[LogicError, webecho.model.WebSocket]] = {
    dependencies.webSocketsBot.webSocketGet(uuid, wsUuid).flatMap {
      case Some(result) => Future.successful(Right(result))
      case None         => Future.successful(Left(RecorderNotFound(uuid)))
    }
  }

  def unregisterWebSocket(uuid: UUID, wsUuid: UUID)(implicit ctx: CommandContext): Future[Either[LogicError, Unit]] = {
    dependencies.webSocketsBot.webSocketDelete(uuid, wsUuid).map {
      case Some(true)  => Right(())
      case Some(false) => Left(SystemError(s"Unable to delete $uuid/$wsUuid"))
      case None        => Left(RecorderNotFound(uuid))
    }
  }

  def checkWebSocketState(uuid: UUID, wsUuid: UUID)(implicit ctx: CommandContext): Future[Either[LogicError, Unit]] = {
    dependencies.webSocketsBot.webSocketAlive(uuid, wsUuid).map {
      case Some(true)  => Right(())
      case Some(false) => Left(SystemError(s"Unable to connect to web socket for $uuid/$wsUuid"))
      case None        => Left(RecorderNotFound(uuid))
    }
  }

  def getServiceInfo()(implicit ctx: CommandContext): Future[Either[LogicError, webecho.model.StoreInfo]] = {
    echoStore.storeInfo() match {
      case Some(info) => Future.successful(Right(info))
      case None       => Future.successful(Left(SystemError("nothing in cache")))
    }
  }

}

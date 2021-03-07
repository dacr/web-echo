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
package webecho.dependencies.websocketsbot

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.Timeout
import org.slf4j.LoggerFactory
import webecho.ServiceConfig
import webecho.dependencies.echostore.EchoStore
import webecho.model.{EchoWebSocket, OperationOrigin}
import webecho.tools.JsonImplicits
import org.json4s.jackson.JsonMethods.parseOpt

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

object BasicWebSocketsBot {
  def apply(config: ServiceConfig, store: EchoStore) = new BasicWebSocketsBot(config, store)
}

class BasicWebSocketsBot(config: ServiceConfig, store: EchoStore) extends WebSocketsBot with JsonImplicits {
  val logger = LoggerFactory.getLogger(getClass)

  // =================================================================================

  sealed trait ConnectManagerCommand

  case class ReceivedContent(content: String) extends ConnectManagerCommand

  def connectBehavior(entryUUID: UUID, webSocket: EchoWebSocket): Behavior[ConnectManagerCommand] = Behaviors.setup { context =>
    logger.info(s"new connect actor spawned for $entryUUID/${webSocket.uuid} ${webSocket.uri}")
    implicit val system = context.system
    implicit val ec = context.executionContext

    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case TextMessage.Strict(text) =>
          context.self ! ReceivedContent(text)
        case TextMessage.Streamed(stream) =>
          val concatenatedText = stream.runReduce(_ + _) // Force consume and concat all responses fragments
          concatenatedText.map(text => context.self ! ReceivedContent(text))
        case BinaryMessage.Strict(bin) =>
          logger.warn(s"Strict binary message not supported ${webSocket.uuid} ${webSocket.uri}")
        case BinaryMessage.Streamed(bin) =>
          logger.warn(s"Streamed binary message not supported  ${webSocket.uuid} ${webSocket.uri}")
          bin.runWith(Sink.ignore) // Force consume (to free input stream)
        case x =>
          logger.error(s"Not understood entry $x ${webSocket.uuid} ${webSocket.uri}")
      }

    val flow = Http().webSocketClientFlow(request = WebSocketRequest(uri = webSocket.uri))

    val (upgradedResponse, closed) =
      Source.never
        .viaMat(flow)(Keep.right)
        .toMat(incoming)(Keep.both)
        .run()

    val connected = upgradedResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        val msg= s"Connection failed: ${upgrade.response.status}"
        logger.error(msg)
        Future.failed(new Exception(msg))
      }
    }

    def updated(receivedCount: Int): Behavior[ConnectManagerCommand] = {
      Behaviors.receiveMessage {
        case ReceivedContent(content) =>
          parseOpt(content) match {
            case None =>
            case Some(jvalue) =>
              store.entryPrependValue(entryUUID, jvalue)
          }
          updated(receivedCount + 1)
      }
    }

    updated(0)
  }

  // =================================================================================

  sealed trait BotCommand

  object StopCommand extends BotCommand

  object SetupCommand extends BotCommand

  case class WebSocketAddCommand(entryUUID: UUID, uri: String, userData: Option[String], origin: Option[OperationOrigin], replyTo: ActorRef[EchoWebSocket]) extends BotCommand

  case class WebSocketGetCommand(entryUUID: UUID, uuid: UUID, replyTo: ActorRef[Option[EchoWebSocket]]) extends BotCommand

  case class WebSocketDeleteCommand(entryUUID: UUID, uuid: UUID, replyTo: ActorRef[Option[Boolean]]) extends BotCommand

  case class WebSocketListCommand(entryUUID: UUID, replyTo: ActorRef[Option[Iterable[EchoWebSocket]]]) extends BotCommand

  case class WebSocketAliveCommand(entryUUID: UUID, uuid: UUID, replyTo: ActorRef[Option[Boolean]]) extends BotCommand

  def botBehavior(): Behavior[BotCommand] = {
    def updated(connections: Map[UUID, ActorRef[ConnectManagerCommand]]): Behavior[BotCommand] = Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case SetupCommand =>
          val connections = for {
            entryUUID <- store.entriesList()
            websocket <- store.webSocketList(entryUUID).getOrElse(Iterable.empty)
          } yield {
            val newActorName = s"websocket-actor-${websocket.uuid}"
            val newActorRef = context.spawn(connectBehavior(entryUUID, websocket), newActorName)
            UUID.fromString(websocket.uuid) -> newActorRef
          }
          updated(connections.toMap)
        case StopCommand =>
          Behaviors.stopped
        case WebSocketAddCommand(entryUUID, uri, userData, origin, replyTo) =>
          val result = store.webSocketAdd(entryUUID, uri, userData, origin)
          replyTo ! result
          Behaviors.same
        case WebSocketGetCommand(entryUUID, uuid, replyTo) =>
          replyTo ! store.webSocketGet(entryUUID, uuid)
          Behaviors.same
        case WebSocketDeleteCommand(entryUUID, uuid, replyTo) =>
          replyTo ! store.webSocketDelete(entryUUID, uuid)
          Behaviors.same
        case WebSocketListCommand(entryUUID, replyTo) =>
          replyTo ! store.webSocketList(entryUUID)
          Behaviors.same
        case WebSocketAliveCommand(entryUUID, uuid, replyTo) =>
          replyTo ! None // TODO - to be continued
          Behaviors.same
      }
    }

    updated(Map.empty)
  }

  implicit val primesSystem: ActorSystem[BotCommand] = ActorSystem(botBehavior(), "WebSocketsBotActorSystem")
  implicit val ec = primesSystem.executionContext
  implicit val timeout: Timeout = 3.seconds

  primesSystem ! SetupCommand

  // =================================================================================

  override def webSocketAdd(entryUUID: UUID, uri: String, userData: Option[String], origin: Option[OperationOrigin]): Future[EchoWebSocket] = {
    primesSystem.ask(WebSocketAddCommand(entryUUID, uri, userData, origin, _))
  }

  override def webSocketGet(entryUUID: UUID, uuid: UUID): Future[Option[EchoWebSocket]] = {
    primesSystem.ask(WebSocketGetCommand(entryUUID, uuid, _))
  }

  override def webSocketDelete(entryUUID: UUID, uuid: UUID): Future[Option[Boolean]] = {
    primesSystem.ask(WebSocketDeleteCommand(entryUUID, uuid, _))
  }

  override def webSocketList(entryUUID: UUID): Future[Option[Iterable[EchoWebSocket]]] = {
    primesSystem.ask(WebSocketListCommand(entryUUID, _))
  }

  override def webSocketAlive(entryUUID: UUID, uuid: UUID): Future[Option[Boolean]] = {
    primesSystem.ask(WebSocketAliveCommand(entryUUID, uuid, _))
  }
}

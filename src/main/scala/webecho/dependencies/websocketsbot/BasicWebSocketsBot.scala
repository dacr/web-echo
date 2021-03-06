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

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.util.Timeout
import webecho.ServiceConfig
import webecho.dependencies.echostore.EchoStore
import webecho.model.{EchoWebSocket, OperationOrigin}

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

object BasicWebSocketsBot {
  def apply(config: ServiceConfig, store: EchoStore) = new BasicWebSocketsBot(config, store)
}

class BasicWebSocketsBot(config: ServiceConfig, store: EchoStore) extends WebSocketsBot {

  sealed trait BotCommand

  object StopCommand extends BotCommand

  def botBehavior(): Behavior[BotCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case StopCommand =>
        Behaviors.stopped
    }
  }

  implicit val primesSystem: ActorSystem[BotCommand] = ActorSystem(botBehavior(), "WebSocketsBotActorSystem")
  implicit val ec = primesSystem.executionContext
  implicit val timeout: Timeout = 3.seconds

  // =================================================================================

  override def webSocketAdd(entryUUID: UUID, uri: String, userData: Option[String], origin: Option[OperationOrigin]): Future[EchoWebSocket] = {
    ???
  }

  override def webSocketGet(entryUUID: UUID, uuid: UUID): Future[Option[EchoWebSocket]] = {
    ???
  }

  override def webSocketDelete(entryUUID: UUID, uuid: UUID): Future[Boolean] = {
    ???
  }

  override def webSocketList(entryUUID: UUID): Future[Option[Iterable[EchoWebSocket]]] = {
    ???
  }

  override def webSocketAlive(entryUUID: UUID, uuid: UUID): Future[Boolean] = {
    ???
  }
}

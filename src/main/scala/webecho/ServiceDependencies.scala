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
package webecho

import webecho.dependencies.echostore.{EchoStore, EchoStoreFileSystem, EchoStoreMemOnly}
import webecho.dependencies.websocketsbot.{BasicWebSocketsBot, WebSocketsBot}
import webecho.security.SecurityService
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory

trait ServiceDependencies {
  val config: ServiceConfig
  val echoStore: EchoStore
  val webSocketsBot: WebSocketsBot
  val securityService: SecurityService
  implicit val system: ActorSystem
}

object ServiceDependencies {
  def defaults: ServiceDependencies = {
    val selectedConfig = ServiceConfig()
    val akkaConfig     = ConfigFactory.load().getConfig("web-echo")
    implicit val sys   = ActorSystem(s"akka-http-${selectedConfig.webEcho.application.code}-system", akkaConfig)
    
    // val selectedStore = EchoCacheMemOnly(selectedConfig)
    val selectedStore  = EchoStoreFileSystem(selectedConfig)
    val security       = new SecurityService(selectedConfig.webEcho.security)

    new ServiceDependencies {
      override val config: ServiceConfig             = selectedConfig
      override val echoStore: EchoStore              = selectedStore
      override val webSocketsBot: BasicWebSocketsBot = BasicWebSocketsBot(selectedConfig, selectedStore)
      override val securityService: SecurityService  = security
      override implicit val system: ActorSystem      = sys
    }
  }
}

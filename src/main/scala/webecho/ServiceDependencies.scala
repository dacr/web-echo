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

trait ServiceDependencies {
  val config: ServiceConfig
  val echoStore: EchoStore
  val webSocketsBot: WebSocketsBot
}

object ServiceDependencies {
  def defaults: ServiceDependencies = {
    val selectedConfig = ServiceConfig()
    // val selectedStore = EchoCacheMemOnly(selectedConfig)
    val selectedStore  = EchoStoreFileSystem(selectedConfig)

    new ServiceDependencies {
      override val config: ServiceConfig             = selectedConfig
      override val echoStore: EchoStore              = selectedStore
      override val webSocketsBot: BasicWebSocketsBot = BasicWebSocketsBot(selectedConfig, selectedStore)
    }
  }
}

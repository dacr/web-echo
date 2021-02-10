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
package webecho

import webecho.dependencies.echocache.{EchoCache, EchoCacheFileSystem, EchoCacheMemOnly}

trait ServiceDependencies {
  val config:ServiceConfig
  val echoCache:EchoCache
}

object ServiceDependencies {
  def defaults:ServiceDependencies = new ServiceDependencies {
    override val config: ServiceConfig = ServiceConfig()
    //override val echoCache: EchoCache = EchoCacheMemOnly(config)
    override val echoCache: EchoCache = EchoCacheFileSystem(config)
  }
}
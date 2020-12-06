/*
 * Copyright 2020 David Crosson
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

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.Logger

import scala.concurrent.{ExecutionContextExecutor, Future}

case class Service(dependencies: ServiceDependencies, servicesRoutes: ServiceRoutes) {
  val appConfig = dependencies.config.webEcho
  val name: String = appConfig.application.code
  val interface: String = appConfig.http.listeningInterface
  val port: Int = appConfig.http.listeningPort

  private val logger: Logger = org.slf4j.LoggerFactory.getLogger(name)
  logger.info(s"Service $name is starting")

  val config = ConfigFactory.load() // akka specific config is accessible under the path named 'web-echo'
  implicit val system: ActorSystem = akka.actor.ActorSystem(s"akka-http-$name-system", config.getConfig("web-echo"))
  implicit val materializer: ActorMaterializer.type = akka.stream.ActorMaterializer
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val bindingFuture: Future[Http.ServerBinding] = Http().newServerAt(interface = interface, port = port).bindFlow(servicesRoutes.routes)
  bindingFuture.map(_ => logger.info(s"Service $name is started"))

  def shutdown(): Unit = {
    bindingFuture.flatMap(_.unbind()).onComplete { _ =>
      logger.info(s"$name http service has shutdown")
      logger.info(s"stopping actor system ${system.name}...")
      system.terminate()
    }
  }
}

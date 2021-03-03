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

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.Logger

import scala.concurrent.{ExecutionContextExecutor, Future}

case class Service(dependencies: ServiceDependencies, servicesRoutes: ServiceRoutes) {
  val appConfig = dependencies.config.webEcho
  val version = appConfig.metaInfo.version
  val appName = appConfig.application.name
  val appCode = appConfig.application.code
  val interface: String = appConfig.http.listeningInterface
  val port: Int = appConfig.http.listeningPort

  private val logger: Logger = org.slf4j.LoggerFactory.getLogger(appCode)
  logger.info(s"$appCode service version $version is starting")

  val config = ConfigFactory.load() // akka specific config is accessible under the path named 'web-echo'
  implicit val system: ActorSystem = akka.actor.ActorSystem(s"akka-http-$appCode-system", config.getConfig("web-echo"))
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val bindingFuture: Future[Http.ServerBinding] = Http().newServerAt(interface = interface, port = port).bindFlow(servicesRoutes.routes)
  bindingFuture.map { _ =>
    logger.info(s"$appCode service is started and listening on $interface:$port")
    logger.info(s"$appCode Embedded swagger user interface ${appConfig.site.swaggerUserInterfaceURL}")
    logger.info(s"$appCode Embedded swagger specification ${appConfig.site.swaggerURL}")
    logger.info(s"$appCode API end point ${appConfig.site.apiURL}")
    logger.info(s"$appCode home page ${appConfig.site.baseURL}")
    logger.info(s"$appCode project page ${appConfig.metaInfo.projectURL} (with configuration documentation) ")
  }

  def shutdown(): Unit = {
    bindingFuture.flatMap(_.unbind()).onComplete { _ =>
      logger.info(s"$appCode http service has shutdown")
      logger.info(s"stopping actor system ${system.name}...")
      system.terminate()
    }
  }

  // Can be used to avoid automatic exit from ammonite scripts
  def waitSystemTerminate():Unit = {
    println("Waiting for end of operations...")
    import scala.concurrent.Await
    import scala.concurrent.duration.Duration
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

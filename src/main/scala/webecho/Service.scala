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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl._
import org.apache.pekko.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.Logger

import scala.concurrent.{ExecutionContextExecutor, Future}

case class Service(dependencies: ServiceDependencies, servicesRoutes: ServiceRoutes) {
  val config = dependencies.config.webEcho
  val version   = config.metaInfo.version
  val appName   = config.application.name
  val appCode   = config.application.code
  val interface = config.http.listeningInterface
  val port      = config.http.listeningPort

  private val logger: Logger = org.slf4j.LoggerFactory.getLogger(appCode)
  logger.info(s"$appCode service version $version is starting")

  // System is now provided by dependencies
  implicit val system: ActorSystem                        = dependencies.system
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  import scala.concurrent.duration.FiniteDuration
  
  // Cleanup task
  config.behavior.cleanupInterval match {
    case interval: FiniteDuration =>
      system.scheduler.scheduleWithFixedDelay(interval, interval) { () =>
        dependencies.echoStore.storeList().foreach { id =>
          dependencies.echoStore.echoInfo(id).foreach { info =>
            info.origin.foreach { origin =>
              info.lifeExpectancy.foreach { life =>
                val expiresAt = origin.createdOn.plus(java.time.Duration.ofMillis(life.toMillis))
                if (java.time.OffsetDateTime.now().isAfter(expiresAt)) {
                  logger.info(s"Cleanup: Deleting expired recorder $id (expired at $expiresAt)")
                  dependencies.echoStore.echoDelete(id)
                }
              }
            }
          }
        }
      }
    case _ => logger.warn("Cleanup interval is not finite, cleanup task not started")
  }

  val bindingFuture: Future[Http.ServerBinding] = Http().newServerAt(interface = interface, port = port).bindFlow(servicesRoutes.routes)
  bindingFuture.map { _ =>
    logger.info(s"$appCode service is started and listening on $interface:$port")
    logger.info(s"$appCode Embedded openapi user interface ${config.site.swaggerUserInterfaceURL}")
    logger.info(s"$appCode Embedded openapi specification ${config.site.swaggerURL}")
    logger.info(s"$appCode API end point ${config.site.apiURL}")
    logger.info(s"$appCode home page ${config.site.baseURL}")
    logger.info(s"$appCode project page ${config.metaInfo.projectURL} (with configuration documentation) ")
  }

  def shutdown(): Unit = {
    bindingFuture.flatMap(_.unbind()).onComplete { _ =>
      logger.info(s"$appCode http service has shutdown")
      logger.info(s"stopping actor system ${system.name}...")
      system.terminate()
    }
  }

  // Can be used to avoid automatic exit from ammonite scripts
  def waitSystemTerminate(): Unit = {
    println("Waiting for end of operations...")
    import scala.concurrent.Await
    import scala.concurrent.duration.Duration
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

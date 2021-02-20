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

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.duration.Duration

case class ApplicationConfig(
  name: String,
  code: String,
)

case class HttpConfig(
  listeningInterface: String,
  listeningPort: Int,
)

case class SiteConfig(
  prefix: Option[String],
  url: String
) {
  val cleanedPrefix = prefix.map(_.trim.replaceAll("/+$", "")).filter(_.size > 0)
  val cleanedURL = url.trim.replaceAll("/+$", "")
  val absolutePrefix = cleanedPrefix.map(p => s"/$p").getOrElse("")
  val baseURL = url + absolutePrefix
  val apiURL = baseURL + "/api"
  val swaggerUserInterfaceURL = s"$baseURL/swagger"
  val swaggerURL = s"$baseURL/swagger/swagger.json"
}

case class FileSystemCacheConfig(
  path: String
)

case class Behavior(
  echoTimeout: Duration,
  fileSystemCache: FileSystemCacheConfig
)

// Automatically populated by the build process from a generated config file
case class WebEchoMetaConfig(
  projectName: Option[String],
  projectGroup: Option[String],
  projectPage: Option[String],
  buildVersion: Option[String],
  buildDateTime: Option[String],
  buildUUID: Option[String],
) {
  def version = buildVersion.getOrElse("x.y.z")
  def dateTime = buildDateTime.getOrElse("?")
  def uuid = buildUUID.getOrElse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
  def projectURL = projectPage.getOrElse("https://github.com/dacr")
}

case class WebEchoConfig(
  application: ApplicationConfig,
  http: HttpConfig,
  site: SiteConfig,
  behavior: Behavior,
  metaInfo: WebEchoMetaConfig
)

// ---------------------------------------------------------------------------------------------------------------------

case class ServiceConfig(
  webEcho: WebEchoConfig
)

object ServiceConfig {
  def apply(): ServiceConfig = {
    val logger = LoggerFactory.getLogger("WebEchoServiceConfig")
    val configSource = {
      val metaConfig = ConfigSource.resources("webecho-meta.conf")
      ConfigSource.default.withFallback(metaConfig.optional)
    }
    configSource.load[ServiceConfig] match {
      case Left(issues) =>
        issues.toList.foreach { issue => logger.error(issue.toString) }
        throw new RuntimeException("Invalid application configuration\n" + issues.toList.map(_.toString).mkString("\n"))
      case Right(config) =>
        config
    }
  }
}
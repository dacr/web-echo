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

import ch.qos.logback.classic.ClassicConstants
import org.slf4j.Logger
import webecho.routing.ApiEndpoints
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.apispec.openapi.Server
import sttp.apispec.openapi.circe._
import io.circe.syntax._
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

object Main {
  System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "webecho/logback.xml")

  def start(): Service = {
    val logger: Logger = org.slf4j.LoggerFactory.getLogger("WebEchoMain")
    logger.info(s"web-echo application is starting")
    val dependencies   = ServiceDependencies.defaults
    val serviceRoutes  = ServiceRoutes(dependencies)
    Service(dependencies, serviceRoutes)
  }

  def generateOpenApiSpecs(path: String): Unit = {
    val config = ServiceConfig().webEcho
    val apiURL = config.site.apiURL
    
    val openApi = OpenAPIDocsInterpreter().toOpenAPI(ApiEndpoints.all, "Web Echo API", "2.0")
      .addServer(Server(apiURL))
      
    val json = openApi.asJson.spaces2
    Files.write(Paths.get(path), json.getBytes(StandardCharsets.UTF_8))
    println(s"OpenAPI spec generated at $path")
    System.exit(0)
  }

  def main(args: Array[String]): Unit = {
    args.toList match {
      case "--just-generate-openapi-specs" :: path :: Nil =>
        generateOpenApiSpecs(path)
      case "--just-generate-openapi-specs" :: _ =>
        System.err.println("Error: Missing path for --just-generate-openapi-specs")
        System.exit(1)
      case _ =>
        start()
    }
  }
}
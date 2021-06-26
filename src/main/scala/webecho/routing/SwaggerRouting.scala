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
package webecho.routing

import akka.http.scaladsl.model.MediaTypes.{`application/json`, `text/html`}
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import webecho.ServiceDependencies
import webecho.tools.Templating
import yamusca.imports._
import yamusca.implicits._

case class SwaggerRouting(dependencies: ServiceDependencies) extends Routing {
  val pageContext                   = PageContext(dependencies.config.webEcho)
  implicit val homeContextConverter = ValueConverter.deriveConverter[PageContext]

  val templating: Templating = Templating(dependencies.config)
  val swaggerJsonLayout      = (context: Context) => templating.makeTemplateLayout("webecho/templates/swagger.json")(context)
  val swaggerUILayout        = (context: Context) => templating.makeTemplateLayout("webecho/templates/swagger-ui.html")(context)

  def swaggerSpec: Route = path("swagger.json") {
    val content     = swaggerJsonLayout(pageContext.asContext)
    val contentType = `application/json`
    complete {
      HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
    }
  }

  def swaggerUI: Route =
    pathEndOrSingleSlash {
      get {
        val content     = swaggerUILayout(pageContext.asContext)
        val contentType = `text/html` withCharset `UTF-8`
        complete {
          HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
        }
      }
    }

  override def routes: Route = pathPrefix("swagger")(swaggerUI ~ swaggerSpec)
}

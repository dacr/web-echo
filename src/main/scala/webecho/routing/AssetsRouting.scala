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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.ContentTypeResolver.Default
import org.webjars.WebJarAssetLocator
import webecho.ServiceDependencies

object AssetsRouting {
  protected val assetLocator = new WebJarAssetLocator()
}

case class AssetsRouting(dependencies: ServiceDependencies) extends Routing {

  private def staticRoutes: Route = {
    val staticResourcesSubDirectories = List("images", "txt")
    val routes                        = for { resourceDirectory <- staticResourcesSubDirectories } yield {
      path(resourceDirectory / RemainingPath) { resource =>
        respondWithHeaders(noClientCacheHeaders) {
          getFromResource(s"webecho/static-content/$resourceDirectory/${resource.toString()}")
        }
      }
    }
    routes.reduce(_ ~ _)
  }

  private def assetsRoutes: Route =
    rejectEmptyResponse {
      path("assets" / Segment / RemainingPath) { (webjar, path) =>
        respondWithHeaders(clientCacheHeaders) {
          val resourcePath = AssetsRouting.assetLocator.getFullPath(webjar, path.toString())
          getFromResource(resourcePath)
        }
      }
    }

  override def routes: Route = assetsRoutes ~ staticRoutes
}

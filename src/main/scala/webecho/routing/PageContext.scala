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
package webecho.routing

import webecho.WebEchoConfig
import webecho.model.EchoesInfo

case class PageContext(
  title: String,
  appcode: String,
  base: String,
  url: String,
  baseURL: String,
  apiURL: String,
  swaggerURL: String,
  swaggerUIURL: String,
  projectURL: String,
  buildVersion: String,
  buildDateTime: String,
  contactEmail: String
)

object PageContext {
  def apply(config: WebEchoConfig) = {
    val site = config.site
    new PageContext(
      title = config.application.name,
      appcode = config.application.code,
      base = site.absolutePrefix,
      url = site.cleanedURL,
      baseURL = site.baseURL,
      apiURL = site.apiURL,
      swaggerURL = site.swaggerURL,
      swaggerUIURL = site.swaggerUserInterfaceURL,
      projectURL = config.metaInfo.projectURL,
      buildVersion = config.metaInfo.version,
      buildDateTime = config.metaInfo.dateTime,
      contactEmail = config.metaInfo.contact
    )
  }
}

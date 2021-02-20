package webecho.routing

import webecho.WebEchoConfig

case class PageContext(
  title: String,
  base: String,
  url: String,
  baseURL: String,
  apiURL: String,
  swaggerURL: String,
  swaggerUIURL: String,
  buildVersion: String,
  buildDateTime: String,
)

object PageContext {
  def apply(webEchoConfig: WebEchoConfig) = {
    val site = webEchoConfig.site
    new PageContext(
      title = webEchoConfig.application.name,
      base = site.absolutePrefix,
      url = site.cleanedURL,
      baseURL = site.baseURL,
      apiURL = site.apiURL,
      swaggerURL = site.swaggerURL,
      swaggerUIURL = site.swaggerUserInterfaceURL,
      buildVersion = webEchoConfig.metaInfo.version,
      buildDateTime = webEchoConfig.metaInfo.dateTime
    )
  }
}


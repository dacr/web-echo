package webecho.routing

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webecho.ServiceDependencies
import akka.http.scaladsl.model.MediaTypes.`text/html`
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import webecho.model.EchoesInfo
import webecho.templates.html.HomeTemplate

case class HomePageContext(page: PageContext, stats: EchoesInfo)

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  override def routes: Route = home

  val site = dependencies.config.webEcho.site

  val pageContext = PageContext(dependencies.config.webEcho)

  def home: Route = pathEndOrSingleSlash {
    get {
      complete {
        val statsOption     = dependencies.echoCache.entriesInfo()
        val stats           = statsOption.getOrElse(EchoesInfo(lastUpdated = 0, count = 0))
        val homePageContext = HomePageContext(pageContext, stats)
        val content         = HomeTemplate.render(homePageContext).toString()
        val contentType     = `text/html` withCharset `UTF-8`
        HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
      }
    }
  }
}

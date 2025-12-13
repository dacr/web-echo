package webecho.routing

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.model.MediaTypes.`text/html`
import org.apache.pekko.http.scaladsl.model.HttpCharsets._
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpResponse}
import webecho.ServiceDependencies
import webecho.model.StoreInfo
import webecho.templates.html.HomeTemplate

case class HomePageContext(page: PageContext, stats: StoreInfo)

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  override def routes: Route = home

  val site = dependencies.config.webEcho.site

  val pageContext = PageContext(dependencies.config.webEcho)

  def home: Route = pathEndOrSingleSlash {
    get {
      complete {
        val statsOption     = dependencies.echoStore.storeInfo()
        val stats           = statsOption.getOrElse(StoreInfo(lastUpdated = None, count = 0))
        val homePageContext = HomePageContext(pageContext, stats)
        val content         = HomeTemplate.render(homePageContext).toString()
        val contentType     = `text/html` withCharset `UTF-8`
        HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
      }
    }
  }
}

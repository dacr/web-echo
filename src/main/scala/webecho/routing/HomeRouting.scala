package webecho.routing

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.model.MediaTypes.`text/html`
import org.apache.pekko.http.scaladsl.model.HttpCharsets._
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpResponse}
import webecho.ServiceDependencies
import webecho.dependencies.echostore.model.EchoesInfo
import webecho.templates.html.HomeTemplate

case class HomePageContext(page: PageContext, stats: EchoesInfo)

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  override def routes: Route = home

  val site = dependencies.config.webEcho.site

  val pageContext = PageContext(dependencies.config.webEcho)

  def home: Route = pathEndOrSingleSlash {
    get {
      complete {
        val statsOption     = dependencies.echoStore.echoesInfo()
        val stats           = statsOption.getOrElse(EchoesInfo(lastUpdated = None, count = 0))
        val homePageContext = HomePageContext(pageContext, stats)
        val content         = HomeTemplate.render(homePageContext).toString()
        val contentType     = `text/html` withCharset `UTF-8`
        HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
      }
    }
  }
}

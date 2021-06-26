package webecho.routing

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webecho.ServiceDependencies
import webecho.tools.Templating
import akka.http.scaladsl.model.MediaTypes.`text/html`
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import webecho.model.EchoesInfo
import yamusca.imports._
import yamusca.implicits._

case class HomePageContext(page: PageContext, stats: EchoesInfo)

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  override def routes: Route = home

  val site = dependencies.config.webEcho.site

  val templating: Templating            = Templating(dependencies.config)
  implicit val echoesInfoConverter      = ValueConverter.deriveConverter[EchoesInfo]
  implicit val pageContextConverter     = ValueConverter.deriveConverter[PageContext]
  implicit val homePageContextConverter = ValueConverter.deriveConverter[HomePageContext]

  val homeLayout  = (context: Context) => templating.makeTemplateLayout("webecho/templates/home.html")(context)
  val pageContext = PageContext(dependencies.config.webEcho)

  def home: Route = pathEndOrSingleSlash {
    get {
      complete {
        val statsOption     = dependencies.echoCache.entriesInfo()
        val stats           = statsOption.getOrElse(EchoesInfo(lastUpdated = 0, count = 0))
        val homePageContext = HomePageContext(pageContext, stats)
        val content         = homeLayout(homePageContext.asContext)
        val contentType     = `text/html` withCharset `UTF-8`
        HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
      }
    }
  }
}

package webecho.routing

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webecho.ServiceDependencies
import webecho.tools.Templating
import akka.http.scaladsl.model.MediaTypes.`text/html`
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import yamusca.imports._
import yamusca.implicits._

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  override def routes: Route = home

  val site = dependencies.config.webEcho.site
  val pageContext = PageContext(dependencies.config.webEcho)

  implicit val homeContextConverter = ValueConverter.deriveConverter[PageContext]
  val templating: Templating = Templating(dependencies.config)
  val homeLayout = (context: Context) => templating.makeTemplateLayout("webecho/templates/home.html")(context)

  def home: Route = pathEndOrSingleSlash {
    get {
      val content = homeLayout(pageContext.asContext)
      val contentType = `text/html` withCharset `UTF-8`
      complete {
        HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
      }
    }
  }
}

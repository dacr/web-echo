package webecho.routing

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webecho.ServiceDependencies
import webecho.tools.Templating
import akka.http.scaladsl.model.MediaTypes.`text/html`
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}

case class HomeRouting(dependencies: ServiceDependencies) extends Routing {
  override def routes: Route = home

  val site = dependencies.config.webEcho.site
  val pageContext = PageContext(dependencies.config.webEcho)

  val templateContext = {
    import yamusca.imports._
    import yamusca.implicits._
    implicit val homeContextConverter = ValueConverter.deriveConverter[PageContext]
    pageContext.asContext
  }
  val templating: Templating = Templating(dependencies.config)


  def home: Route = pathEndOrSingleSlash {
    get {
      val content = templating.layout("webecho/templates/home.html", templateContext)
      val contentType = `text/html` withCharset `UTF-8`
      complete {
        HttpResponse(entity = HttpEntity(contentType, content), headers = noClientCacheHeaders)
      }
    }
  }
}

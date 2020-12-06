package webecho

trait ServiceDependencies {
  val config:ServiceConfig
}

object ServiceDependencies {
  def defaults:ServiceDependencies = new ServiceDependencies {
    override val config: ServiceConfig = ServiceConfig()
  }
}
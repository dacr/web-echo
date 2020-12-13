package webecho

import webecho.dependencies.echocache.{EchoCache, EchoCacheMemOnly}

trait ServiceDependencies {
  val config:ServiceConfig
  val echoCache:EchoCache
}

object ServiceDependencies {
  def defaults:ServiceDependencies = new ServiceDependencies {
    override val config: ServiceConfig = ServiceConfig()
    override val echoCache: EchoCache = EchoCacheMemOnly(config)
  }
}
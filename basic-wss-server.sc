//> using scala "3.7.4"
//> using dep "org.apache.pekko::pekko-http:1.3.0"
//> using dep "org.apache.pekko::pekko-stream:1.4.0"
//> using dep "org.slf4j:slf4j-simple:2.0.17"

import org.apache.pekko.http.scaladsl._
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{Duration, DurationInt}

// ---------------------------------------------------------------------------------------------------------------------
// Just a helper function which can be removed as only used for some println
def lanAddresses(): List[String] = {
  import scala.jdk.CollectionConverters._
  java.net.NetworkInterface
    .getNetworkInterfaces().asScala
    .filterNot(_.isLoopback)
    .filterNot(_.isVirtual)
    .filter(_.isUp)
    .toList
    .flatMap { interface =>
      val ips = interface
        .getInetAddresses.asScala
        .to(List)
        .filterNot(_.isAnyLocalAddress)
        .collect { case x: java.net.Inet4Address => x.getHostAddress }
      ips.headOption
    }
}
// ---------------------------------------------------------------------------------------------------------------------


val port = args.headOption.map(_.toInt).getOrElse(8080)
val interface = args.drop(1).headOption.getOrElse("0.0.0.0")
System.setProperty("pekko.http.server.remote-address-header", "true")
System.setProperty("pekko.http.server.remote-address-attribute", "true")
System.setProperty("pekko.http.server.websocket.periodic-keep-alive-max-idle", "1 second")

given system:org.apache.pekko.actor.ActorSystem = org.apache.pekko.actor.ActorSystem("MySystem")
given executor:ExecutionContextExecutor = system.dispatcher

val routes = pathEndOrSingleSlash {
  extractClientIP { clientIP =>
    val from = clientIP.toIP.map(_.ip.getHostAddress)
    println(s"new connection from $from")
    val tickSource = Source.tick(2.seconds, 5.second, 0)
    val integers = Iterator.from(0)
    val tickMessageSource = tickSource.map(_ => TextMessage(s"""{"tick":${integers.next()}}"""))
    extractWebSocketUpgrade{ ws =>
      complete {
        ws.handleMessagesWithSinkSource(Sink.ignore, tickMessageSource)
      }
    }
  }
}
Http().newServerAt(interface, port).bind(routes).andThen { case _ =>
  val addr = lanAddresses().head
  println(s"Listening for websocket clients on $interface:$port ")
  println(s"Use this URI to connect to this server : ws://$addr:$port")
}


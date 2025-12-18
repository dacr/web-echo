package webecho.tools

import java.net.{InetAddress, URI}
import scala.util.Try

object NetworkTools {

  def validateWebSocketUri(uriString: String): Either[String, URI] = {
    Try(new URI(uriString)).toEither.left.map(_ => "Invalid URI format").flatMap { uri =>
      val scheme = Option(uri.getScheme).map(_.toLowerCase)
      if (!scheme.contains("ws") && !scheme.contains("wss")) {
        Left("Only ws:// or wss:// schemes are allowed")
      } else {
        val host = uri.getHost
        if (host == null || host.isEmpty) {
          Left("Missing host in URI")
        } else if (isUnsafeHost(host)) {
          Left(s"Host '$host' is not allowed for security reasons")
        } else {
          Right(uri)
        }
      }
    }
  }

  def isUnsafeHost(host: String): Boolean = {
    val blockedHosts = Set("localhost", "0.0.0.0")
    if (blockedHosts.contains(host.toLowerCase)) return true

    Try(InetAddress.getAllByName(host)).toOption match {
      case Some(addresses) =>
        addresses.exists { addr =>
          addr.isLoopbackAddress ||
            addr.isSiteLocalAddress ||
            addr.isLinkLocalAddress ||
            addr.isAnyLocalAddress ||
            isMetadataService(addr)
        }
      case None =>
        // If we can't resolve it, it's safer to block it if it looks like an IP
        // but for now let's assume if it doesn't resolve it might be handled by the client later or fail.
        // However, standard SSRF protection often blocks what it can't verify if it's suspicious.
        false 
    }
  }

  private def isMetadataService(addr: InetAddress): Boolean = {
    // Cloud metadata service IP: 169.254.169.254
    addr.getHostAddress == "169.254.169.254"
  }
}

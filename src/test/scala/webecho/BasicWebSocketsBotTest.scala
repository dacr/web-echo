package webecho

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import webecho.dependencies.echostore.EchoStoreMemOnly
import webecho.dependencies.websocketsbot.BasicWebSocketsBot
import webecho.model.WebSocket

import java.util.UUID
import scala.concurrent.{ExecutionContext, Promise}
import scala.concurrent.duration._

class BasicWebSocketsBotTest extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("WebSocketDeleteTest")
  implicit val ec: ExecutionContext = system.dispatcher

  override def afterAll(): Unit = {
    system.terminate()
  }

  "BasicWebSocketsBot" should {
    "terminate connection when websocket is deleted" in {
      val closePromise = Promise[Boolean]()
      
      // 1. Start a simple WebSocket server
      val route = path("ws") {
        extractWebSocketUpgrade { upgrade =>
            val incoming = Sink.onComplete { _ => 
                closePromise.success(true)
            }
            val outgoing = Source.maybe[Message]
            complete(upgrade.handleMessagesWithSinkSource(incoming, outgoing))
        }
      }
      
      val binding = Http().newServerAt("localhost", 0).bind(route).futureValue
      val port = binding.localAddress.getPort
      val wsUri = s"ws://localhost:$port/ws"
      
      // 2. Setup Bot
      val config = ServiceConfig()
      val store = EchoStoreMemOnly(config)
      val bot = BasicWebSocketsBot(config, store)
      
      val entryUUID = UUID.randomUUID()
      store.echoAdd(entryUUID, None, None)

      // 3. Add WebSocket
      val ws = bot.webSocketAdd(entryUUID, wsUri, None, None, None).futureValue
      
      // Give it a moment to connect
      Thread.sleep(1000)
      
      // 4. Delete WebSocket
      bot.webSocketDelete(entryUUID, ws.id).futureValue shouldBe Some(true)
      
      // 5. Check if server received close signal
      whenReady(closePromise.future, timeout(10.seconds)) { closed =>
        closed shouldBe true
      }
      
      binding.unbind()
    }

    "terminate connection when websocket expires" in {
      val closePromise = Promise[Boolean]()
      
      // 1. Start a simple WebSocket server
      val route = path("ws_expiry") {
        extractWebSocketUpgrade { upgrade =>
            val incoming = Sink.onComplete { _ => 
                closePromise.success(true)
            }
            val outgoing = Source.maybe[Message]
            complete(upgrade.handleMessagesWithSinkSource(incoming, outgoing))
        }
      }
      
      val binding = Http().newServerAt("localhost", 0).bind(route).futureValue
      val port = binding.localAddress.getPort
      val wsUri = s"ws://localhost:$port/ws_expiry"
      
      // 2. Setup Bot
      val config = ServiceConfig()
      val store = EchoStoreMemOnly(config)
      val bot = BasicWebSocketsBot(config, store)
      
      val entryUUID = UUID.randomUUID()
      store.echoAdd(entryUUID, None, None)

      // 3. Add WebSocket with expiration
      val expiresAt = java.time.OffsetDateTime.now().plusSeconds(3)
      val ws = bot.webSocketAdd(entryUUID, wsUri, None, None, Some(expiresAt)).futureValue
      
      // 4. Check if server received close signal automatically
      whenReady(closePromise.future, timeout(10.seconds)) { closed =>
        closed shouldBe true
      }
      
      // 5. Verify it is removed from store
      // Wait a bit more to ensure actor processes the deletion
      Thread.sleep(500)
      store.webSocketGet(entryUUID, ws.id) shouldBe empty
      
      binding.unbind()
    }
  }
}

package webecho

import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import webecho.apimodel.{ApiWebSocket, ApiWebSocketSpec, ApiRecorder, ApiNotFound, ApiRecord}
import webecho.dependencies.echostore.{EchoStore, EchoStoreMemOnly}
import webecho.dependencies.websocketsbot.WebSocketsBot
import webecho.model.{WebSocket, Origin}
import webecho.routing.ApiRoutes
import webecho.tools.JsonSupport
import org.apache.pekko.http.scaladsl.model.StatusCodes
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
import com.github.plokhotnyuk.jsoniter_scala.core._
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.util.ByteString
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.HttpEntity

trait JsoniterScalaSupportTest {
  implicit def unmarshaller[A](implicit codec: JsonValueCodec[A]): FromEntityUnmarshaller[A] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map {
        case ByteString.empty => throw Unmarshaller.NoContentException
        case data             => readFromArray(data.toArray)
      }
  
  implicit def marshaller[A](implicit codec: JsonValueCodec[A]): Marshaller[A, RequestEntity] =
     Marshaller.withFixedContentType(MediaTypes.`application/json`) { a =>
       HttpEntity(MediaTypes.`application/json`, writeToArray(a))
     }
}

class ApiRoutesTest extends AnyWordSpec with Matchers with ScalatestRouteTest with JsonSupport with JsoniterScalaSupportTest {

  // Resolve ambiguity for ApiWebSocketSpec marshaller
  implicit val apiWebSocketSpecMarshaller: Marshaller[ApiWebSocketSpec, RequestEntity] = marshaller(apiWebSocketInputCodec)

  val config = ServiceConfig()
  val echoStore = EchoStoreMemOnly(config)
  
  // Mock WebSocketsBot to capture arguments
  class MockWebSocketsBot extends WebSocketsBot {
    var lastExpiresAt: Option[OffsetDateTime] = None

    override def webSocketAdd(entryUUID: UUID, uri: String, userData: Option[String], origin: Option[Origin], expiresAt: Option[OffsetDateTime]): Future[WebSocket] = {
      lastExpiresAt = expiresAt
      Future.successful(WebSocket(UUID.randomUUID(), uri, userData, origin, expiresAt))
    }

    override def webSocketGet(entryUUID: UUID, uuid: UUID): Future[Option[WebSocket]] = Future.successful(None)
    override def webSocketDelete(entryUUID: UUID, uuid: UUID): Future[Option[Boolean]] = Future.successful(Some(true))
    override def webSocketList(entryUUID: UUID): Future[Option[Iterable[WebSocket]]] = Future.successful(Some(Nil))
    override def webSocketAlive(entryUUID: UUID, uuid: UUID): Future[Option[Boolean]] = Future.successful(Some(true))
  }

  val mockBot = new MockWebSocketsBot()

  val dependencies = new ServiceDependencies {
    override val config: ServiceConfig = ApiRoutesTest.this.config
    override val echoStore: EchoStore = ApiRoutesTest.this.echoStore
    override val webSocketsBot: WebSocketsBot = mockBot
  }

  val routes = ApiRoutes(dependencies).routes

  "ApiRoutes" should {
    "use default expiration when no expire param provided" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None)
      val spec = ApiWebSocketSpec("ws://localhost", None, None)
      
      Post(s"/api/v2/recorder/$recorderId/websocket", spec) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        mockBot.lastExpiresAt should be (defined)
        val duration = java.time.Duration.between(OffsetDateTime.now(), mockBot.lastExpiresAt.get)
        duration.toMinutes shouldBe 15L +- 1L
      }
    }

    "use provided expiration when valid" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None)
      val spec = ApiWebSocketSpec("ws://localhost", None, Some("10m"))
      
      Post(s"/api/v2/recorder/$recorderId/websocket", spec) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        mockBot.lastExpiresAt should be (defined)
        val duration = java.time.Duration.between(OffsetDateTime.now(), mockBot.lastExpiresAt.get)
        duration.toMinutes shouldBe 10L +- 1L
      }
    }

    "cap expiration at max duration" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None)
      val spec = ApiWebSocketSpec("ws://localhost", None, Some("10h")) // Max is 4h
      
      Post(s"/api/v2/recorder/$recorderId/websocket", spec) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        mockBot.lastExpiresAt should be (defined)
        val duration = java.time.Duration.between(OffsetDateTime.now(), mockBot.lastExpiresAt.get)
        duration.toMinutes shouldBe 240L +- 1L
      }
    }
    
    "handle short notation like 60s" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None)
      val spec = ApiWebSocketSpec("ws://localhost", None, Some("60s"))
      
      Post(s"/api/v2/recorder/$recorderId/websocket", spec) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        mockBot.lastExpiresAt should be (defined)
        val duration = java.time.Duration.between(OffsetDateTime.now(), mockBot.lastExpiresAt.get)
        duration.toSeconds shouldBe 60L +- 5L
      }
    }

    "return 404 for unknown recorder" in {
      val recorderId = UUID.randomUUID()
      // Do not add recorder to store
      val spec = ApiWebSocketSpec("ws://localhost", None, None)
      
      Post(s"/api/v2/recorder/$recorderId/websocket", spec) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[ApiNotFound] shouldBe ApiNotFound("Unknown UUID")
      }
    }

    "return records as NDJSON" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None)
      
      val data1 = Map("msg" -> "hello")
      val data2 = Map("msg" -> "world")
      
      // Add data to store
      val enriched1 = Map(
        "data" -> data1,
        "addedOn" -> OffsetDateTime.now().toString,
        "addedByRemoteHostAddress" -> Some("127.0.0.1"),
        "addedByUserAgent" -> Some("test-agent")
      )
      val enriched2 = Map(
        "data" -> data2,
        "addedOn" -> OffsetDateTime.now().toString,
        "addedByRemoteHostAddress" -> Some("127.0.0.1"),
        "addedByUserAgent" -> Some("test-agent")
      )
      
      echoStore.echoAddContent(recorderId, enriched1)
      echoStore.echoAddContent(recorderId, enriched2)
      
      Get(s"/api/v2/recorder/$recorderId/records") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val responseBody = responseAs[String]
        val lines = responseBody.split("\n")
        lines should have size 2
        
        val content = lines.mkString("\n")
        content should include ("hello")
        content should include ("world")
        
        // Verify each line is valid JSON
        lines.foreach { line =>
          readFromString[ApiRecord](line)
        }
      }
    }
  }
}

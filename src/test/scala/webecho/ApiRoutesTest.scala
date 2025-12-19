package webecho

import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import webecho.apimodel.{ApiWebSocket, ApiWebSocketSpec, ApiRecorder, ApiRecorderUpdate, ApiErrorNotFound, ApiRecord, ApiReceiptProof}
import webecho.dependencies.echostore.{EchoStore, EchoStoreMemOnly}
import webecho.dependencies.websocketsbot.WebSocketsBot
import webecho.model.{WebSocket, Origin}
import webecho.routing.ApiRoutes
import webecho.tools.JsonSupport.given
import org.apache.pekko.http.scaladsl.model.StatusCodes
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}

import webecho.security.SecurityService
import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import JsoniterScalaTestSupport.given

class ApiRoutesTest extends AnyWordSpec with Matchers with ScalatestRouteTest {

  val config = ServiceConfig(ConfigFactory.parseString("web-echo.security.ssrf-protection-enabled = false"))
  val echoStore = EchoStoreMemOnly(config)
  val securityService = new SecurityService(config.webEcho.security)(using system)
  
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
    override val securityService: SecurityService = ApiRoutesTest.this.securityService
    override val system: ActorSystem = ApiRoutesTest.this.system
  }

  val routes = ApiRoutes(dependencies).routes

  "ApiRoutes" should {
    "use default expiration when no expire param provided" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None, None)
      val spec = ApiWebSocketSpec("ws://localhost", None, None)
      
      Post(s"/api/v2/recorder/$recorderId/websocket", spec) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        mockBot.lastExpiresAt should be(defined)
        val duration = java.time.Duration.between(OffsetDateTime.now(), mockBot.lastExpiresAt.get)
        duration.toMinutes shouldBe 15L +- 1L
      }
    }

    "use provided expiration when valid" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None, None)
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
      echoStore.echoAdd(recorderId, None, None)
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
      echoStore.echoAdd(recorderId, None, None)
      val spec = ApiWebSocketSpec("ws://localhost", None, Some("60s"))
      
      Post(s"/api/v2/recorder/$recorderId/websocket", spec) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        mockBot.lastExpiresAt should be (defined)
        val duration = java.time.Duration.between(OffsetDateTime.now(), mockBot.lastExpiresAt.get)
        duration.toSeconds shouldBe 60L +- 5L
      }
    }

    "update recorder description" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, Some("initial"), None)
      val update = ApiRecorderUpdate(Some("updated"))
      
      import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
      Put(s"/api/v2/recorder/$recorderId", update) ~> addCredentials(OAuth2BearerToken("dummy")) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val recorder = responseAs[ApiRecorder]
        recorder.description shouldBe Some("updated")
      }
    }

    "return 404 for unknown recorder" in {
      val recorderId = UUID.randomUUID()
      // Do not add recorder to store
      val spec = ApiWebSocketSpec("ws://localhost", None, None)
      
      Post(s"/api/v2/recorder/$recorderId/websocket", spec) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[ApiErrorNotFound] shouldBe ApiErrorNotFound("Unknown UUID")
      }
    }

    "return records as NDJSON" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None, None)
      
      val data1 = Map("msg" -> "hello")
      val data2 = Map("msg" -> "world")
      
      // Add data to store
      val enriched1 = Map(
        "data" -> data1,
        "addedOn" -> OffsetDateTime.now().toString,
        "webhook" -> Map(
          "remoteHostAddress" -> Some("127.0.0.1"),
          "userAgent" -> Some("test-agent")
        )
      )
      val enriched2 = Map(
        "data" -> data2,
        "addedOn" -> OffsetDateTime.now().toString,
        "webhook" -> Map(
          "remoteHostAddress" -> Some("127.0.0.1"),
          "userAgent" -> Some("test-agent")
        )
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
        
        // Verify each line is valid JSON and has receiptProof
        lines.foreach { line =>
          val record = readFromString[ApiRecord](line)
          record.receiptProof should be (defined)
        }
      }
    }

    "receive data via record endpoint (GET, PUT, POST)" in {
      val recorderId = UUID.randomUUID()
      echoStore.echoAdd(recorderId, None, None)

      // Test GET
      Get(s"/api/v2/record/$recorderId?msg=hello") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proof = responseAs[ApiReceiptProof]
        proof.sha256 shouldNot be(empty)
      }

      // Test PUT
      val dataPut = Map("msg" -> "put-data")
      Put(s"/api/v2/record/$recorderId", dataPut) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proof = responseAs[ApiReceiptProof]
        proof.sha256 shouldNot be(empty)
      }

      // Test POST
      val dataPost = Map("msg" -> "post-data")
      Post(s"/api/v2/record/$recorderId", dataPost) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proof = responseAs[ApiReceiptProof]
        proof.sha256 shouldNot be(empty)
      }
    }
  }
}

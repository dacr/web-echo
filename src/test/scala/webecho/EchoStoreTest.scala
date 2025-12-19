package webecho

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues.*
import webecho.dependencies.echostore.{EchoStoreFileSystem, EchoStoreMemOnly}
import webecho.tools.{JsonSupport, UniqueIdentifiers}
import com.github.plokhotnyuk.jsoniter_scala.core.*
import webecho.model.{WebSocket, Record}

import java.nio.file.{Files, Paths}
import scala.util.Try

class EchoStoreTest extends AnyWordSpec with should.Matchers with BeforeAndAfterAll with JsonSupport {

  val tmpPath          = Files.createTempDirectory("webecho-test-data")
  val customizedConfig = ConfigFactory.parseString(s"""
                                                      |web-echo.behavior.file-system-cache.path="$tmpPath"
                                                      |""".stripMargin)
  val testConfig       = ServiceConfig(customizedConfig)

  override def afterAll(): Unit = {
    FileUtils.deleteDirectory(tmpPath.toFile)
  }

  "Echo store" can {
    val stores = Map(
      "filesystem store" -> EchoStoreFileSystem(testConfig),
      "memory store"     -> EchoStoreMemOnly(testConfig)
    )
    for ((storeName, store) <- stores) {
      s"$storeName" can {
        "manage entries" can {
          "list nothing" in {
            store.storeList() shouldBe empty
          }
          "create" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
          }
          "get nothing" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            store.echoGet(entryUUID).value shouldBe empty
          }
          "delete" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            store.echoDelete(entryUUID)
            store.echoGet(entryUUID) shouldBe empty
          }
          "prepend data" in {
            val entryUUID  = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            val rawData    = Map("a" -> 42, "b" -> "truc")
            val recordData = Map(
              "data"                     -> rawData,
              "addedOn"                  -> "2023-01-01T00:00:00Z",
              "addedByRemoteHostAddress" -> Some("1.2.3.4"),
              "addedByUserAgent"         -> Some("Agent")
            )
            store.echoAddContent(entryUUID, recordData)
            val result     = store.echoGet(entryUUID).value.to(List)
            result should have size 1

            val record = result.headOption.value
            val data   = record.data
            data shouldBe a[Map[?, ?]]
            val asMap  = data.asInstanceOf[Map[String, Any]]
            asMap("b") shouldBe "truc"
            asMap("a") shouldBe 42.0
          }
        }
        "manage websockets" can {
          "create and get" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            val result    = store.webSocketAdd(entryUUID, "ws://somewhere/connect", None, None, None)
            val gotten    = store.webSocketGet(entryUUID, result.id)
            gotten.value shouldBe result
          }
          "list" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect1", None, None, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect2", None, None, None)
            store.webSocketList(entryUUID).value.size shouldBe 2
          }
          "delete" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect", None, None, None)
            val wss       = store.webSocketList(entryUUID)
            val uuid      = wss.value.headOption.value.id
            store.webSocketDelete(entryUUID, uuid)
            store.webSocketGet(entryUUID, uuid) shouldBe empty
          }
        }
      }
    }
  }
}

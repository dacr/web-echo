package webecho

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.json4s.Extraction.decompose
import org.json4s.JsonDSL._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues._
import webecho.dependencies.echostore.{EchoStoreFileSystem, EchoStoreMemOnly}
import webecho.tools.JsonImplicits

import java.nio.file.{Files, Paths}
import java.util.UUID

class EchoStoreTest extends AnyWordSpec with should.Matchers with BeforeAndAfterAll with JsonImplicits {

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
          "create" in {
            val entryUUID = UUID.randomUUID()
            store.entryAdd(entryUUID, None)
          }
          "get nothing" in {
            val entryUUID = UUID.randomUUID()
            store.entryAdd(entryUUID, None)
            store.entryGet(entryUUID).value shouldBe empty
          }
          "delete" in {
            val entryUUID = UUID.randomUUID()
            store.entryAdd(entryUUID, None)
            store.entryDelete(entryUUID)
            store.entryGet(entryUUID) shouldBe empty
          }
          "prepend data" in {
            val entryUUID   = UUID.randomUUID()
            store.entryAdd(entryUUID, None)
            val testedValue = Map("a" -> 42, "b" -> "truc")
            store.entryPrependValue(entryUUID, decompose(testedValue))
            val result      = store.entryGet(entryUUID).value.to(List)
            result should have size 1
            result.headOption.value.extractOpt[Map[String, _]].value shouldBe testedValue
          }
        }
        "manage websockets" can {
          "create and get" in {
            val entryUUID = UUID.randomUUID()
            store.entryAdd(entryUUID, None)
            val result    = store.webSocketAdd(entryUUID, "ws://somewhere/connect", None, None)
            val gotten    = store.webSocketGet(entryUUID, UUID.fromString(result.uuid))
            gotten.value shouldBe result
          }
          "list" in {
            val entryUUID = UUID.randomUUID()
            store.entryAdd(entryUUID, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect1", None, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect2", None, None)
            store.webSocketList(entryUUID).value.size shouldBe 2
          }
          "delete" in {
            val entryUUID = UUID.randomUUID()
            store.entryAdd(entryUUID, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect", None, None)
            val wss       = store.webSocketList(entryUUID)
            val uuid      = wss.value.headOption.value.uuid
            store.webSocketDelete(entryUUID, UUID.fromString(uuid))
            store.webSocketGet(entryUUID, UUID.fromString(uuid)) shouldBe empty
          }
        }
      }
    }
  }
}

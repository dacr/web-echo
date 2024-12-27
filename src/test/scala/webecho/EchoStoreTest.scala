package webecho

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.json4s.Extraction.decompose
import org.json4s.JsonDSL.*
import org.json4s.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues.*
import webecho.dependencies.echostore.{EchoStoreFileSystem, EchoStoreMemOnly}
import webecho.tools.{JsonImplicits, UniqueIdentifiers}

import java.nio.file.{Files, Paths}

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
            val entryUUID   = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            val testedValue = Map("a" -> 42, "b" -> "truc")
            store.echoAddValue(entryUUID, decompose(testedValue))
            val result      = store.echoGet(entryUUID).value.to(List)
            result should have size 1
            result.headOption.value.extractOpt[Map[String, ?]].value shouldBe testedValue
          }
        }
        "manage websockets" can {
          "create and get" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            val result    = store.webSocketAdd(entryUUID, "ws://somewhere/connect", None, None)
            val gotten    = store.webSocketGet(entryUUID, result.uuid)
            gotten.value shouldBe result
          }
          "list" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect1", None, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect2", None, None)
            store.webSocketList(entryUUID).value.size shouldBe 2
          }
          "delete" in {
            val entryUUID = UniqueIdentifiers.randomUUID()
            store.echoAdd(entryUUID, None)
            store.webSocketAdd(entryUUID, "ws://somewhere/connect", None, None)
            val wss       = store.webSocketList(entryUUID)
            val uuid      = wss.value.headOption.value.uuid
            store.webSocketDelete(entryUUID, uuid)
            store.webSocketGet(entryUUID, uuid) shouldBe empty
          }
        }
      }
    }
  }
}

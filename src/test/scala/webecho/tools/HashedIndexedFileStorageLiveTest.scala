package webecho.tools

import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues.*
import org.scalatest.TryValues.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.util.UUID
import scala.util.Success

class HashedIndexedFileStorageLiveTest extends AnyWordSpec with should.Matchers with BeforeAndAfterAll {
  def createTmpDir(testName: String): String = {
    val tmpDir = new File(scala.util.Properties.tmpDir)
    val dir    = new File(tmpDir, s"web-echo-$testName-storage-${UUID.randomUUID}")
    dir.mkdirs()
    dir.getAbsolutePath
  }
  "Hashed indexed file storage" can {
    "record data" in {
      val store = HashedIndexedFileStorageLive(createTmpDir("record")).get
      store.append("data1").isSuccess shouldBe true
      store.append("data2").isSuccess shouldBe true
      store.count().get shouldBe 2
    }
    "record data safely" in {
      val store     = HashedIndexedFileStorageLive(createTmpDir("record-check")).get
      val resultSHA = store.append("data1").get
      resultSHA.toString shouldBe "5b41362bc82b7f3d56edc5a306db22105707d01ff4819e26faef9724a2d406c9"
      store.count().get shouldBe 1
    }
    "record data safely as a kind of blockchain" in {
      val store      = HashedIndexedFileStorageLive(createTmpDir("record-block-chain")).get
      val data1sha   = "5b41362bc82b7f3d56edc5a306db22105707d01ff4819e26faef9724a2d406c9"
      val result1SHA = store.append("data1").get
      result1SHA.toString shouldBe data1sha
      val data2sha   = SHA256Engine.digest("data2".getBytes("UTF8"), List(SHA.fromString(data1sha).bytes))
      val result2SHA = store.append("data2").get
      result2SHA.toString shouldBe data2sha.toString
    }
    "not record empty data" in {
      val store = HashedIndexedFileStorageLive(createTmpDir("record-nothing")).get
      store.append("").isSuccess shouldBe false
    }
    "list empty" in {
      val store = HashedIndexedFileStorageLive(createTmpDir("list-empty")).get
      store.list().get.toList should have length (0)
    }
    "list recorded data" in {
      val store = HashedIndexedFileStorageLive(createTmpDir("list-recorded")).get
      val data  = 1.to(20).map(i => f"data$i%03d").toList
      data.foreach(store.append)
      store.list().get.toList shouldBe data
    }
    "list recorded data in reverse order" in {
      val store = HashedIndexedFileStorageLive(createTmpDir("list-recorded-reverse")).get
      val data  = 1.to(20).map(i => f"data$i%03d").toList
      data.foreach(store.append)
      store.list(reverseOrder = true).get.toList shouldBe data.reverse
    }
  }
}

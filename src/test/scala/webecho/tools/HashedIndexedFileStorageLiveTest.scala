package webecho.tools

import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues.*
import org.scalatest.TryValues.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import webecho.tools.hashedindexedstorage.{HashedIndexedFileStorageLive, SHA256Engine, SHAGoal}

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
      val nowGetter = {
        var current = 500L
        () => {
          current += 1L;
          current
        }
      }
      val store     = HashedIndexedFileStorageLive(createTmpDir("record-check"), nowGetter = nowGetter).get
      val resultSHA = store.append("data1").get.sha
      resultSHA.toString shouldBe "2e4b9fb56e07d17dc00bb736952587f70882f79f41edc4d30dec1e4ffbdf5054"
      store.count().get shouldBe 1
      val data1sha  = SHA256Engine.digest(
        "data1".getBytes("UTF8"),
        List(
          HashedIndexedFileStorageLive.int2bytes(0),     // nonce
          HashedIndexedFileStorageLive.long2bytes(501L), // timestamp
          HashedIndexedFileStorageLive.long2bytes(0L)    // index
        )
      )
      resultSHA.toString shouldBe data1sha.toString

    }
    "record data safely as a kind of blockchain" in {
      val nowGetter  = {
        var current = 500L
        () => {
          current += 1L;
          current
        }
      }
      val store      = HashedIndexedFileStorageLive(createTmpDir("record-block-chain"), nowGetter = nowGetter).get
      val data1sha   = "2e4b9fb56e07d17dc00bb736952587f70882f79f41edc4d30dec1e4ffbdf5054"
      val result1SHA = store.append("data1").get.sha
      result1SHA.toString shouldBe data1sha
      val result2SHA = store.append("data2").get.sha
      val data2sha   = SHA256Engine.digest(
        "data2".getBytes("UTF8"),
        List(
          HashedIndexedFileStorageLive.int2bytes(0),     // nonce
          HashedIndexedFileStorageLive.long2bytes(502L), // timestamp
          HashedIndexedFileStorageLive.long2bytes(1L),   // index
          // SHA.fromString(data1sha).bytes
          result1SHA.bytes
        )
      )
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
    "list recorded data from a given timestamp" in {
      val nowGetter = {
        var current = 0L
        () => { current += 1L; current }
      }

      val store = HashedIndexedFileStorageLive(createTmpDir("list-recorded-from-timestamp"), nowGetter = nowGetter).get
      val data  = 1.to(20).map(i => f"data$i%03d").toList
      data.foreach(store.append)
      store.list(epoch = Some(14L)).get.toList shouldBe data.drop(13)
    }
    "list recorded data from a given timestamp in reverse order" in {
      val nowGetter = {
        var current = 0L
        () => { current += 1L; current }
      }

      val store = HashedIndexedFileStorageLive(createTmpDir("list-recorded-from-timestamp"), nowGetter = nowGetter).get
      val data  = 1.to(20).map(i => f"data$i%03d").toList
      data.foreach(store.append)
      store.list(reverseOrder = true, epoch = Some(6L)).get.toList shouldBe data.take(6).reverse
    }
    "list recorded data from an approximative timestamp" in {
      val nowGetter = {
        var current = 0L
        () => {
          current += 10L;
          current
        }
      }

      val store = HashedIndexedFileStorageLive(createTmpDir("list-recorded-from-approximative-timestamp"), nowGetter = nowGetter).get
      val data  = 1.to(20).map(i => f"data${i * 10}%03d").toList
      data.foreach(store.append)
      store.list(epoch = Some(95L)).get.toList shouldBe data.drop(9)
    }
    "list recorded data from an approximative timestamp in reverse order" in {
      val nowGetter = {
        var current = 0L
        () => {
          current += 10L;
          current
        }
      }

      val store = HashedIndexedFileStorageLive(createTmpDir("list-recorded-from-approximative-timestamp-reverse"), nowGetter = nowGetter).get
      val data  = 1.to(20).map(i => f"data${i * 10}%03d").toList
      data.foreach(store.append)
      store.list(reverseOrder = true, epoch = Some(95L)).get.toList shouldBe data.take(9).reverse
    }

    "record data using blockchain nonce and goal" in {
      val goal  = SHAGoal.standard(2) // time increase exponentially of course when the length is increased
      val store = HashedIndexedFileStorageLive(createTmpDir("record"), shaGoal = Some(goal)).get
      for {
        d1 <- store.append("data1")
        d2 <- store.append("data2")
        d3 <- store.append("data3")
      } yield List(d1, d2, d3).map(_.sha).forall(goal.check) shouldBe true
      store.count().get shouldBe 3
    }

  }
}

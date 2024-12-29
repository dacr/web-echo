package webecho.tools

import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues.*
import org.scalatest.TryValues.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.util.UUID

class HashedIndexedFileStorageLiveTest extends AnyWordSpec with should.Matchers with BeforeAndAfterAll {
  def createTmpDir(testName:String): String = {
    val tmpDir = new File(scala.util.Properties.tmpDir)
    val dir    = new File(tmpDir, s"web-echo-$testName-storage-${UUID.randomUUID}")
    dir.mkdirs()
    dir.getAbsolutePath
  }
  "Hashed indexed file storage" can {
    "append data" in {
      val store   = HashedIndexedFileStorageLive(createTmpDir("appending")).get
      store.append("data1").isSuccess shouldBe true
      store.append("data2").isSuccess shouldBe true
      store.size().get shouldBe 2

    }
    "list appended data" in {
      val store   = HashedIndexedFileStorageLive(createTmpDir("appending")).get
      val data = 1.to(100).map(i => s"data$i").toList
      data.foreach(store.append)
      store.list().get.toList shouldBe data
    }
  }
}

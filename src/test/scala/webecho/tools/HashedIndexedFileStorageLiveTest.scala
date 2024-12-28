package webecho.tools

import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues.*
import org.scalatest.TryValues.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.util.UUID

class HashedIndexedFileStorageLiveTest extends AnyWordSpec with should.Matchers with BeforeAndAfterAll {
  def createTmpDir(): File = {
    val tmpDir = new File(scala.util.Properties.tmpDir)
    val dir    = new File(tmpDir, s"hashed-indexed-file-storage-${UUID.randomUUID}")
    dir.mkdirs()
    dir
  }
  "Hashed indexed file storage" can {
    "append data" in {
      val usedDir = createTmpDir()
      val prefix  = "testme"
      val store   = HashedIndexedFileStorageLive(usedDir.getAbsolutePath, prefix).get
      store.append("data1").isSuccess shouldBe true
      store.append("data2").isSuccess shouldBe true
      store.size().get shouldBe 2

    }
    "list appended data" in {}
  }
}

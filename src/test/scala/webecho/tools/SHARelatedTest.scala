package webecho.tools

import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues.*
import org.scalatest.TryValues.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.util.UUID
import scala.util.Success

class SHARelatedTest extends AnyWordSpec with should.Matchers with BeforeAndAfterAll {

  "SHA tools" can {
    "compute hashes" in {
      val hash              = SHA256Engine.digest("data42".getBytes("UTF-8"))
      val hashString        = hash.toString
      val expected          = "e29142fda136eb086a960c13e97fe4a3d6ea8c46e45243ccac4813b4e54927ee"
      val rebuiltHash       = SHA.fromString(hash.toString)
      val rebuildHashString = rebuiltHash.toString

      hashString shouldBe expected
      rebuildHashString shouldBe expected
    }
  }
}

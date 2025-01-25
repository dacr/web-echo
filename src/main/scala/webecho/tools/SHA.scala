package webecho.tools

import java.math.BigInteger
import java.util.HexFormat

trait SHA {
  def bytes: Array[Byte]
  override def toString: String = bytes.map("%02x".format(_)).mkString
}

case class SHA1(bytes: Array[Byte])   extends SHA
case class SHA256(bytes: Array[Byte]) extends SHA

object SHA {
  private val shaRE = """^[a-f0-9]+$""".r

  def fromString(input: String): SHA = {
    val encoded = input.trim.toLowerCase
    if (encoded.length % 2 != 0 || !shaRE.matches(encoded)) throw new RuntimeException(s"Not a SHA string")
    else if (encoded.length == SHA1Engine.size * 2) SHA1(HexFormat.of().parseHex(input))
    else if (encoded.length == SHA256Engine.size * 2) SHA256(HexFormat.of().parseHex(input))
    else throw new RuntimeException(s"Invalid SHA string")
  }
}

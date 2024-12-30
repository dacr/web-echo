package webecho.tools

import java.math.BigInteger

object SHA {

  type SHA1 = Array[Byte]
  val SHA1_SIZE = 20

  def sha1(that: Array[Byte], extra: Option[Array[Byte]] = None): SHA1 = {
    import java.security.MessageDigest
    val md = MessageDigest.getInstance("SHA-1")
    md.update(that)
    extra.foreach(bytes => md.update(bytes))

    val digest = md.digest() // ALWAYS 20 bytes for SHA-1
    if (digest.length != SHA1_SIZE) throw new RuntimeException("Invalid SHA1 size")
    digest
  }

  type SHA256 = Array[Byte]
  val SHA256_SIZE = 32

  def sha256(that: Array[Byte], extra: Option[Array[Byte]] = None): SHA256 = {
    import java.security.MessageDigest
    val md = MessageDigest.getInstance("SHA-256")
    md.update(that)
    extra.foreach(bytes => md.update(bytes))

    val digest = md.digest() // ALWAYS 32 bytes for SHA-56
    if (digest.length != SHA256_SIZE) throw new RuntimeException("Invalid SHA256 size")
    digest
  }

  def sha2string(sha: Array[Byte]): String = {
    sha.map("%02x".format(_)).mkString
  }
  def string2sha(input:String): Array[Byte] = {
    BigInteger(input, 16).toByteArray
  }
}

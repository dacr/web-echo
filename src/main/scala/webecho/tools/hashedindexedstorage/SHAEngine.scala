package webecho.tools.hashedindexedstorage

trait SHAEngine {
  def size: Int
  def algo: String
  def digest(that: Array[Byte], extras: List[Array[Byte]] = Nil): SHA
  def fromBytes(input: Array[Byte]): SHA
}

object SHA1Engine extends SHAEngine {
  override val size = 20

  override val algo = "SHA-1"

  override def digest(that: Array[Byte], extras: List[Array[Byte]] = Nil): SHA = {
    import java.security.MessageDigest
    val md = MessageDigest.getInstance(algo)
    md.update(that)
    extras.foreach(bytes => md.update(bytes))

    val digest = md.digest() // ALWAYS 20 bytes for SHA-1
    if (digest.length != size) throw new RuntimeException(s"Invalid size for $algo")
    SHA1(digest)
  }

  def fromBytes(input: Array[Byte]): SHA = {
    if (input.length == size) SHA1(input)
    else throw new RuntimeException(s"Not $algo bytes")
  }

}

object SHA256Engine extends SHAEngine {
  override val size = 32

  override val algo = "SHA-256"

  override def digest(that: Array[Byte], extras: List[Array[Byte]] = Nil): SHA = {
    import java.security.MessageDigest
    val md = MessageDigest.getInstance(algo)
    md.update(that)
    extras.foreach(bytes => md.update(bytes))

    val digest = md.digest() // ALWAYS 32 bytes for SHA-56
    if (digest.length != size) throw new RuntimeException(s"Invalid size $algo")
    SHA256(digest)
  }

  def fromBytes(input: Array[Byte]): SHA = {
    if (input.length == size) SHA256(input)
    else throw new RuntimeException(s"Not $algo bytes")
  }

}

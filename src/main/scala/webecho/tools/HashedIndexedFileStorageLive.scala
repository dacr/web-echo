package webecho.tools

import scala.util.{Try, Using}
import java.io.{File, FileOutputStream, ObjectOutputStream, RandomAccessFile}
import scala.io.Codec

/** This is a quick and dirty first implementation
  *   - Not thread safe
  *   - Not protected against full file system events
  *   - Not optimized
  */
object HashedIndexedFileStorageLive {
  def apply(
    targetDirectory: String,
    storageFileBasename: String,
    dataFileSuffix: String = ".data",
    indexFileSuffix: String = ".index",
    codec: Codec = Codec.UTF8 // recommended codec for json
  ): Try[HashedIndexedFileStorage] = {
    val target    = File(targetDirectory)
    val dataFile  = File(target, s"$storageFileBasename$dataFileSuffix")
    val indexFile = File(target, s"$storageFileBasename$indexFileSuffix")
    Try {
      target.mkdirs()
      dataFile.createNewFile()
      indexFile.createNewFile()
      new HashedIndexedFileStorageLive(dataFile, indexFile, codec)
    }
  }
}

trait CloseableIterator[T] extends Iterator[T] with AutoCloseable {
  override def hasNext: Boolean = ???

  override def next(): T = ???

  override def close(): Unit = ???
}

object CloseableIterator {
  def empty[T]: CloseableIterator[T] = new CloseableIterator[T] {
    override def hasNext: Boolean = false

    override def next(): T = throw new NoSuchElementException("Iterator is empty")

    override def close(): Unit = ()
  }
}

type Timestamp = Long
type SHA1      = Array[Byte]

def sha1(that: Array[Byte], extra: Option[Array[Byte]]=None): Array[Byte] = {
  import java.security.MessageDigest
  val md = MessageDigest.getInstance("SHA-1")
  md.update(that)
  extra.foreach(bytes => md.update(bytes))
  md.digest() // ALWAYS 20 bytes for SHA-1
}

case class IndexEntry(
  timestamp: Timestamp,
  dataIndex: Long,
  length: Int,
  sha1: SHA1
)

object IndexEntry {
  // timestamp (Long) + index (Long) + record size (Int) + sha1 (20 bytes)
  val size: Int = 8 + 8 + 4 + 20
}

private class HashedIndexedFileStorageLive(
  dataFile: File,
  indexFile: File,
  codec: Codec
) extends HashedIndexedFileStorage {

  private def indexReadEntry(randIndexFile: RandomAccessFile, index: Long): Try[IndexEntry] = Try {
    randIndexFile.seek(index)
    val timestamp = randIndexFile.readLong()
    val dataIndex = randIndexFile.readLong()
    val length    = randIndexFile.readInt()
    val sha1      = Array.ofDim[Byte](20)
    randIndexFile.read(sha1)
    IndexEntry(
      timestamp = timestamp,
      dataIndex = dataIndex,
      length = length,
      sha1 = sha1
    )
  }

  private def indexFileIterator(
    randIndexFile: RandomAccessFile,
    reverseOrder: Boolean,
    fromEpoch: Option[Long]
  ): Try[CloseableIterator[IndexEntry]] = {
    Try {
      fromEpoch match {
        case _ if randIndexFile.length() == 0 => CloseableIterator.empty
        case None if reverseOrder             =>
          var step  = -IndexEntry.size
          var index = randIndexFile.length() - IndexEntry.size
          ???
        case None                             =>
          var index = 0
          var step  = IndexEntry.size
          ???
        case Some(epoch)                      =>
          ???
      }
    }
  }

  def list(reverseOrder: Boolean = false, fromEpoch: Option[Long] = None): Try[CloseableIterator[String]] = {
    Try {
      val randIndexFile = RandomAccessFile(indexFile, "r")
      ???
    }
  }

  def append(data: String): Try[Unit] = {
    Try {
      val bytes     = data.getBytes(codec.charSet)
      val len       = bytes.length
      val timestamp = System.currentTimeMillis()
      val dataIndex = dataFile.length()
      val datasha1  = sha1(bytes) // TODO append previous record SHA1 to chain data
      Using(new FileOutputStream(dataFile, true)) { output =>
        output.write(bytes)
        output.write('\n')
      }
      Using(new ObjectOutputStream(new FileOutputStream(indexFile))) { output =>
        output.writeLong(timestamp)
        output.writeLong(dataIndex)
        output.writeInt(len)
        output.write(datasha1)
      }
    }
  }

  def size(): Try[Long] = {
    Try {
      indexFile.length() / IndexEntry.size
    }
  }

}

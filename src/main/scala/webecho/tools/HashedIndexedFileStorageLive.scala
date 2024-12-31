package webecho.tools

import scala.util.{Failure, Try, Using}
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
    storageFileBasename: String = "default",
    dataFileSuffix: String = ".data",
    indexFileSuffix: String = ".index",
    codec: Codec = Codec.UTF8, // recommended codec for json
    shaEngine: SHAEngine = SHA256Engine
  ): Try[HashedIndexedFileStorage] = {
    val target    = File(targetDirectory)
    val dataFile  = File(target, s"$storageFileBasename$dataFileSuffix")
    val indexFile = File(target, s"$storageFileBasename$indexFileSuffix")
    Try {
      target.mkdirs()
      dataFile.createNewFile()
      indexFile.createNewFile()
      new HashedIndexedFileStorageLive(dataFile, indexFile, codec, shaEngine)
    }
  }
}

type Timestamp = Long

case class IndexEntry(
  timestamp: Timestamp,
  dataIndex: Long,
  length: Int,
  sha: SHA
)

object IndexEntry {
  // timestamp (Long) + index (Long) + record size (Int) + chosen SHA size
  def size(shaEngine: SHAEngine): Int = 8 + 8 + 4 + shaEngine.size
}

private class HashedIndexedFileStorageLive(
  dataFile: File,
  indexFile: File,
  codec: Codec,
  shaEngine: SHAEngine
) extends HashedIndexedFileStorage {

  val indexEntrySize = IndexEntry.size(shaEngine)

  private def indexReadEntry(randIndexFile: RandomAccessFile, index: Long): Try[IndexEntry] = Try {
    randIndexFile.seek(index)
    val timestamp = randIndexFile.readLong()
    val dataIndex = randIndexFile.readLong()
    val length    = randIndexFile.readInt()
    val rawSha    = Array.ofDim[Byte](shaEngine.size)
    randIndexFile.read(rawSha)
    IndexEntry(
      timestamp = timestamp,
      dataIndex = dataIndex,
      length = length,
      sha = shaEngine.fromBytes(rawSha)
    )
  }

  private def buildIndexIterator(
    randIndexFile: RandomAccessFile,
    reverseOrder: Boolean,
    fromEpoch: Option[Long]
  ): Try[CloseableIterator[IndexEntry]] = {
    Try {
      fromEpoch match {
        case _ if randIndexFile.length() == 0 => CloseableIterator.empty
        case None                             =>
          var step  = if (reverseOrder) -indexEntrySize else indexEntrySize
          val index = if (reverseOrder) randIndexFile.length() - indexEntrySize else 0

          new CloseableIterator[IndexEntry] {
            private var currentIndex                  = index
            private var nextEntry: Option[IndexEntry] = None

            // Load the next entry
            private def loadNext(): Unit = {
              if (currentIndex >= 0 && currentIndex < randIndexFile.length()) {
                indexReadEntry(randIndexFile, currentIndex) match {
                  case scala.util.Success(entry) =>
                    nextEntry = Some(entry)
                    currentIndex += step
                  case scala.util.Failure(_)     =>
                    nextEntry = None
                }
              } else {
                nextEntry = None
              }
            }

            // Initialize the first load
            loadNext()

            override def hasNext: Boolean = nextEntry.isDefined

            override def next(): IndexEntry = {
              if (!hasNext) throw new NoSuchElementException("No more entries in the iterator")
              val result = nextEntry.get
              loadNext()
              result
            }

            override def close(): Unit = randIndexFile.close()
          }
        case Some(epoch)                      =>
          ???
      }
    }
  }

  def buildDataIterator(indexIterator: CloseableIterator[IndexEntry]): Try[CloseableIterator[String]] = {
    for {
      dataIndexFile <- Try(RandomAccessFile(dataFile, "r"))
    } yield {
      new CloseableIterator[String] {
        override def hasNext: Boolean = indexIterator.hasNext

        override def close(): Unit = indexIterator.close()

        override def next(): String = {
          val entry = indexIterator.next()
          dataIndexFile.seek(entry.dataIndex)
          val bytes = Array.ofDim[Byte](entry.length)
          dataIndexFile.read(bytes)
          new String(bytes, codec.charSet)
        }
      }
    }

  }

  def list(reverseOrder: Boolean = false, fromEpoch: Option[Long] = None): Try[CloseableIterator[String]] = {
    for {
      randIndexFile <- Try(RandomAccessFile(indexFile, "r"))
      indexIterator <- buildIndexIterator(randIndexFile, reverseOrder, fromEpoch)
      dataIterator  <- buildDataIterator(indexIterator)
    } yield dataIterator
  }

  private def getCurrentLastEntrySHA(indexFile: RandomAccessFile): Option[Array[Byte]] = {
    if (indexFile.length() == 0) None
    else {
      val offset = indexFile.length() - indexEntrySize
      val entry  = indexReadEntry(indexFile, offset)
      entry.toOption.map(_.sha.bytes)
    }
  }

  def append(data: String): Try[SHA] = {
    val bytes = data.getBytes(codec.charSet)
    if (bytes.length == 0) Failure(IllegalArgumentException("Input string is empty"))
    else {
      Using(new FileOutputStream(dataFile, true)) { output =>
        val dataIndex = dataFile.length()
        output.write(bytes)
        output.write('\n')
        output.flush()
        dataIndex
      }.flatMap { dataIndex =>
        Using(new RandomAccessFile(indexFile, "rwd")) { output =>
          val prevLastSHA = getCurrentLastEntrySHA(output)
          val dataSHA     = shaEngine.digest(bytes, prevLastSHA)
          val timestamp   = System.currentTimeMillis()
          output.seek(output.length())
          output.writeLong(timestamp)
          output.writeLong(dataIndex)
          output.writeInt(bytes.length)
          output.write(dataSHA.bytes)
          dataSHA
        }
      }
    }
  }

  def size(): Try[Long] = {
    Try {
      indexFile.length() / indexEntrySize
    }
  }

}

package webecho.tools

import scala.util.{Failure, Try, Success, Using}
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
    shaEngine: SHAEngine = SHA256Engine,
    shaGoal: Option[SHAGoal] = None,
    nowGetter: () => Timestamp = () => System.currentTimeMillis()
  ): Try[HashedIndexedFileStorage] = {
    val target    = File(targetDirectory)
    val dataFile  = File(target, s"$storageFileBasename$dataFileSuffix")
    val indexFile = File(target, s"$storageFileBasename$indexFileSuffix")
    Try {
      target.mkdirs()
      dataFile.createNewFile()
      indexFile.createNewFile()
      new HashedIndexedFileStorageLive(dataFile, indexFile, codec, shaEngine, shaGoal, nowGetter)
    }
  }
}

private class HashedIndexedFileStorageLive(
  dataFile: File,
  indexFile: File,
  codec: Codec,
  shaEngine: SHAEngine,
  shaGoal: Option[SHAGoal],
  nowGetter: () => Timestamp
) extends HashedIndexedFileStorage {

  val indexEntrySize = HashedIndexEntry.size(shaEngine)

  private def indexReadEntry(randIndexFile: RandomAccessFile, offset: Long): Try[HashedIndexEntry] = Try {
    randIndexFile.seek(offset)
    val timestamp  = randIndexFile.readLong()
    val nonce      = randIndexFile.readInt()
    val dataIndex  = randIndexFile.readLong()
    val dataLength = randIndexFile.readInt()
    val rawSha     = Array.ofDim[Byte](shaEngine.size)
    randIndexFile.read(rawSha)
    HashedIndexEntry(
      index = offset, // Not stored, deducted from seek offset in index file
      timestamp = timestamp,
      nonce = nonce,
      dataIndex = dataIndex,
      dataLength = dataLength,
      dataSHA = shaEngine.fromBytes(rawSha)
    )
  }

  private def newCloseableIterator(randIndexFile: RandomAccessFile, step: Int, offset: Long) = {
    new CloseableIterator[HashedIndexEntry] {
      private var currentOffset                       = offset
      private var nextEntry: Option[HashedIndexEntry] = None

      // Load the next entry
      private def readNext(): Unit = {
        if (currentOffset >= 0 && currentOffset < randIndexFile.length()) {
          indexReadEntry(randIndexFile, currentOffset) match {
            case scala.util.Success(entry) =>
              nextEntry = Some(entry)
              currentOffset += step
            case scala.util.Failure(_)     =>
              nextEntry = None
          }
        } else {
          nextEntry = None
        }
      }

      // Initialize the first load
      readNext()

      override def hasNext: Boolean = nextEntry.isDefined

      override def next(): HashedIndexEntry = {
        if (!hasNext) throw new NoSuchElementException("No more entries in the iterator")
        val result = nextEntry.get
        readNext()
        result
      }

      override def close(): Unit = randIndexFile.close()
    }
  }

  private def searchNearestOffsetFor(
    randIndexFile: RandomAccessFile,
    reverseOrder: Boolean,
    fromEpoch: Long
  ): Long = {
    // Implementing using a dichotomy algorithm to search nearest offset based on the `timestamp` field
    val entryCount = randIndexFile.length() / indexEntrySize

    @annotation.tailrec
    def binarySearch(low: Long, high: Long): Long = {
      if (low >= high) {
        val closestOffset = low * indexEntrySize
        if (closestOffset >= 0 && closestOffset < randIndexFile.length()) closestOffset
        else -1 // Return -1 if no valid offset found
      } else {
        val mid            = (low + high) / 2
        val midOffset      = mid * indexEntrySize
        val midEntryOption = indexReadEntry(randIndexFile, midOffset)

        midEntryOption match {
          case Success(midEntry) if midEntry.timestamp == fromEpoch                 => midOffset // Exact match
          case Success(midEntry) if midEntry.timestamp < fromEpoch && !reverseOrder => binarySearch(mid + 1, high)
          case Success(midEntry) if midEntry.timestamp > fromEpoch && !reverseOrder => binarySearch(low, mid - 1)
          case Success(midEntry) if midEntry.timestamp > fromEpoch && reverseOrder  => binarySearch(low, mid - 1)
          case Success(midEntry) if midEntry.timestamp < fromEpoch && reverseOrder  => binarySearch(mid + 1, high)
          case Failure(_)                                                           =>
            -1 // Return -1 on failure to read entry
        }
      }
    }

    if (entryCount == 0) -1 // Return -1 if no entries exist
    else binarySearch(0, entryCount - 1)
  }

  private def buildIndexIterator(
    randIndexFile: RandomAccessFile,
    reverseOrder: Boolean,
    fromEpoch: Option[Long]
  ): Try[CloseableIterator[HashedIndexEntry]] = {
    Try {
      fromEpoch match {
        case _ if randIndexFile.length() == 0 =>
          CloseableIterator.empty

        case None =>
          val step   = if (reverseOrder) -indexEntrySize else indexEntrySize
          val offset = if (reverseOrder) randIndexFile.length() - indexEntrySize else 0
          newCloseableIterator(randIndexFile, step, offset)

        case Some(epoch) =>
          val step   = if (reverseOrder) -indexEntrySize else indexEntrySize
          val offset = searchNearestOffsetFor(randIndexFile, reverseOrder, epoch)
          newCloseableIterator(randIndexFile, step, offset)
      }
    }
  }

  private def buildDataIterator(indexIterator: CloseableIterator[HashedIndexEntry]): Try[CloseableIterator[String]] = {
    for {
      dataIndexFile <- Try(RandomAccessFile(dataFile, "r"))
    } yield {
      new CloseableIterator[String] {
        override def hasNext: Boolean = indexIterator.hasNext

        override def close(): Unit = indexIterator.close()

        override def next(): String = {
          val entry = indexIterator.next()
          dataIndexFile.seek(entry.dataIndex)
          val bytes = Array.ofDim[Byte](entry.dataLength)
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

  private def getIndexLastEntry(indexFile: RandomAccessFile): Option[HashedIndexEntry] = {
    if (indexFile.length() == 0) None
    else {
      val offset = indexFile.length() - indexEntrySize
      val entry  = indexReadEntry(indexFile, offset)
      entry.toOption
    }
  }

  def int2bytes(value: Int): Array[Byte]   = BigInt(value).toByteArray
  def long2bytes(value: Long): Array[Byte] = BigInt(value).toByteArray

  def append(data: String): Try[SHA] = {
    val bytes = data.getBytes(codec.charSet)
    if (bytes.isEmpty) Failure(IllegalArgumentException("Input string is empty"))
    else {
      Using(new FileOutputStream(dataFile, true)) { output =>
        val dataIndex = dataFile.length()
        output.write(bytes)
        output.write('\n')
        output.flush()
        dataIndex
      }.flatMap { dataIndex =>
        Using(new RandomAccessFile(indexFile, "rwd")) { output =>
          val prevEntry = getIndexLastEntry(output)
          val index     = prevEntry.map(_.index + 1L)
          // TODO implement nonce compute
          // TODO take into account record position
          val nonce     = 0
          val extras    =
            List.empty[Array[Byte]] ++
              // index.map(int2bytes) ++
              prevEntry.map(_.dataSHA.bytes)
          val dataSHA   = shaEngine.digest(bytes, extras)
          val timestamp = nowGetter()
          output.seek(output.length())
          output.writeLong(timestamp)
          output.writeInt(nonce)
          output.writeLong(dataIndex)
          output.writeInt(bytes.length)
          output.write(dataSHA.bytes)
          dataSHA
        }
      }
    }
  }

  def count(): Try[Long] = {
    Try {
      indexFile.length() / indexEntrySize
    }
  }

  override def lastUpdated(): Try[Option[Timestamp]] = {
    Using(new RandomAccessFile(indexFile, "r")) { indexFile =>
      getIndexLastEntry(indexFile).map(_.timestamp)
    }
  }
}

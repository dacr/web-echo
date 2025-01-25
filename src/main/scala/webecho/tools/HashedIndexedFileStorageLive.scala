package webecho.tools

import scala.util.{Failure, Try, Success, Using}
import java.io.{File, FileOutputStream, ObjectOutputStream, RandomAccessFile}
import scala.io.Codec

/** Fast, immutable, append-only data storage. Data and meta information are separated in order to make the data file directly and easily readable with standard tools when stored data is just text
  * (grep, tail, ...)
  *
  * Stored data immutability is guaranteed using a blockchain inspired algorithm using chained hash function. When data is appended a hash is returned, which can be used to verify data integrity from
  * user side.
  *
  * IF shaGoal is enabled then it will trigger the nonce compute in order to get a hash starting with the given bytes. Of course performances are impacted.
  *
  * This is a quick and dirty first implementation
  *   - Not thread safe
  *   - Not protected against full file system events
  *   - Not optimized
  */
object HashedIndexedFileStorageLive {
  def apply(
    targetDirectory: String,
    storageFileBasename: String = "default",
    dataFileSuffix: String = ".data",
    metaFileSuffix: String = ".meta",
    codec: Codec = Codec.UTF8,       // recommended codec for json
    shaEngine: SHAEngine = SHA256Engine,
    shaGoal: Option[SHAGoal] = None, // If enabled will trigger the use of the nonce field to reach the given goal
    nowGetter: () => Long = () => System.currentTimeMillis()
  ): Try[HashedIndexedFileStorage] = {
    val target   = File(targetDirectory)
    val dataFile = File(target, s"$storageFileBasename$dataFileSuffix")
    val metaFile = File(target, s"$storageFileBasename$metaFileSuffix")
    Try {
      target.mkdirs()
      dataFile.createNewFile()
      metaFile.createNewFile()
      new HashedIndexedFileStorageLive(dataFile, metaFile, codec, shaEngine, shaGoal, nowGetter)
    }
  }

  def int2bytes(value: Int): Array[Byte]   = BigInt(value).toByteArray
  def long2bytes(value: Long): Array[Byte] = BigInt(value).toByteArray

}

// =====================================================================================================================

private class HashedIndexedFileStorageLive(
  dataFile: File,
  metaFile: File,
  codec: Codec,
  shaEngine: SHAEngine,
  shaGoal: Option[SHAGoal],
  nowGetter: () => Long
) extends HashedIndexedFileStorage {

  private val metaEntrySize = HashedIndexedMetaInternal.size(shaEngine)

  // -------------------------------------------------------------------------------------------------------------------
  private def metaEntryRead(metaAccess: RandomAccessFile, offset: Long): Try[HashedIndexedMetaInternal] = Try {
    metaAccess.seek(offset)
    val timestamp  = metaAccess.readLong()
    val nonce      = metaAccess.readInt()
    val dataOffset = metaAccess.readLong()
    val dataLength = metaAccess.readInt()
    val rawSha     = Array.ofDim[Byte](shaEngine.size)
    metaAccess.read(rawSha)
    HashedIndexedMetaInternal(
      offset = offset, // Not stored, deducted from seek offset in index file
      timestamp = timestamp,
      nonce = nonce,
      dataOffset = dataOffset,
      dataLength = dataLength,
      dataSHA = shaEngine.fromBytes(rawSha)
    )
  }

  // -------------------------------------------------------------------------------------------------------------------
  private def newCloseableIterator(
    metaAccess: RandomAccessFile,
    step: Int,
    offset: Long,
    epoch: Option[Long],
    reverseOrder: Boolean
  ) = {
    new CloseableIterator[HashedIndexedMetaInternal] {
      private var nextOffset = offset
      private var nextMeta = Option.empty[HashedIndexedMetaInternal]

      private def readNext(): Unit = {
        if (nextOffset >= 0 && nextOffset < metaAccess.length()) {
          metaEntryRead(metaAccess, nextOffset) match {
            case Success(entry) =>
              nextMeta = Some(entry)
              if (reverseOrder) nextOffset -= step
              else nextOffset += step
            case Failure(_)     =>
              nextMeta = None
          }
        } else {
          nextMeta = None
        }
      }

      // ------ bootstrap and filter any undesired entry
      readNext()
      while (
        epoch.isDefined && (
          (reverseOrder && nextMeta.exists(_.timestamp > epoch.get)) ||
            (!reverseOrder && nextMeta.exists(_.timestamp < epoch.get))
        )
      ) readNext()
      // ------

      override def hasNext: Boolean = nextMeta.isDefined

      override def next(): HashedIndexedMetaInternal = {
        if (!hasNext) throw new NoSuchElementException("No more entries in the iterator")
        val result = nextMeta.get
        readNext()
        result
      }

      override def close(): Unit = metaAccess.close()
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  private def searchNearestOffsetFor(
    metaAccess: RandomAccessFile,
    epoch: Long
  ): Option[Long] = {
    @annotation.tailrec
    def binarySearch(lowIndex: Long, highIndex: Long): Option[Long] = {
      if (lowIndex >= highIndex) {
        val closestOffset = lowIndex * metaEntrySize
        if (closestOffset >= 0 && closestOffset < metaAccess.length()) Some(closestOffset)
        else None
      } else {
        val midIndex  = (lowIndex + highIndex) / 2
        val midOffset = midIndex * metaEntrySize
        metaEntryRead(metaAccess, midOffset) match {
          case Success(midEntry) if midEntry.timestamp == epoch => Some(midOffset)
          case Success(midEntry) if midEntry.timestamp > epoch  => binarySearch(lowIndex, midIndex - 1)
          case Success(midEntry)                                => binarySearch(midIndex + 1, highIndex)
          case Failure(_)                                       => None
        }
      }
    }

    val entryCount = metaAccess.length() / metaEntrySize
    if (entryCount == 0) None
    else binarySearch(0, entryCount - 1)
  }

  // -------------------------------------------------------------------------------------------------------------------
  private def buildIndexIterator(
    metaAccess: RandomAccessFile,
    reverseOrder: Boolean,
    epoch: Option[Long]
  ): Try[CloseableIterator[HashedIndexedMetaInternal]] = {
    Try {
      epoch match {
        case _ if metaAccess.length() == 0 =>
          CloseableIterator.empty

        case None =>
          val step   = metaEntrySize
          val offset = if (reverseOrder) metaAccess.length() - metaEntrySize else 0
          newCloseableIterator(metaAccess, step, offset, epoch, reverseOrder)

        case Some(fromEpoch) =>
          val step = metaEntrySize
          searchNearestOffsetFor(metaAccess, fromEpoch) match {
            case Some(offset) => newCloseableIterator(metaAccess, step, offset, epoch, reverseOrder)
            case None         => CloseableIterator.empty
          }
      }
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  private def buildDataIterator(
    metaIterator: CloseableIterator[HashedIndexedMetaInternal]
  ): Try[CloseableIterator[String]] = {
    for {
      dataAccess <- Try(RandomAccessFile(dataFile, "r"))
    } yield {
      new CloseableIterator[String] {
        override def hasNext: Boolean = metaIterator.hasNext

        override def close(): Unit = metaIterator.close()

        override def next(): String = {
          val entry = metaIterator.next()
          dataAccess.seek(entry.dataOffset)
          val bytes = Array.ofDim[Byte](entry.dataLength)
          dataAccess.read(bytes)
          new String(bytes, codec.charSet)
        }
      }
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  def list(
    reverseOrder: Boolean = false,
    epoch: Option[Long] = None
  ): Try[CloseableIterator[String]] = {
    for {
      metaAccess   <- Try(RandomAccessFile(metaFile, "r"))
      metaIterator <- buildIndexIterator(metaAccess, reverseOrder, epoch)
      dataIterator <- buildDataIterator(metaIterator)
    } yield dataIterator
  }

  // -------------------------------------------------------------------------------------------------------------------
  private def getIndexLastEntry(
    metaAccess: RandomAccessFile
  ): Option[HashedIndexedMetaInternal] = {
    if (metaAccess.length() == 0) None
    else {
      val offset = metaAccess.length() - metaEntrySize
      val entry  = metaEntryRead(metaAccess, offset)
      entry.toOption
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  private def computeHash(
    dataBytes: Array[Byte],
    timestamp: Long,
    metaIndex: Long,
    prevEntry: Option[HashedIndexedMetaInternal]
  ): (Int, SHA) = {
    val fixedExtras =
      List.empty[Array[Byte]] ++
        Some(HashedIndexedFileStorageLive.long2bytes(timestamp)) ++
        Some(HashedIndexedFileStorageLive.long2bytes(metaIndex)) ++
        prevEntry.map(_.dataSHA.bytes)
    shaGoal match {
      case Some(goal) =>
        // blockchain penalty enabled, it will become more and more costly to try to alter the data !
        LazyList
          .iterate(0)(_ + 1)
          .map(nonce => nonce -> shaEngine.digest(dataBytes, HashedIndexedFileStorageLive.int2bytes(nonce.toInt)::fixedExtras))
          .find((nonce,sha) => goal.check(sha))
          .get // TODO we can iterate up to Int.MaxValue !! but ok not the right code here
      case None       =>
        val nonce   = 0
        val extras  = HashedIndexedFileStorageLive.int2bytes(nonce) :: fixedExtras
        val dataSHA = shaEngine.digest(dataBytes, extras)
        (nonce, dataSHA)
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  def append(data: String): Try[HashedIndexedMeta] = {
    val bytes = data.getBytes(codec.charSet)
    if (bytes.isEmpty) Failure(IllegalArgumentException("Input string is empty"))
    else {
      Using(new FileOutputStream(dataFile, true)) { dataOutput =>
        val dataOffset = dataFile.length()
        dataOutput.write(bytes)
        dataOutput.write('\n')
        dataOutput.flush()
        dataOffset
      }.flatMap { dataOffset =>
        Using(new RandomAccessFile(metaFile, "rwd")) { metaOutput =>
          val prevEntry        = getIndexLastEntry(metaOutput)
          val metaIndex        = prevEntry.map(_.offset / metaEntrySize + 1L).getOrElse(0L)
          val timestamp        = nowGetter()
          val (nonce, dataSHA) = computeHash(dataBytes = bytes, timestamp = timestamp, metaIndex = metaIndex, prevEntry = prevEntry)
          metaOutput.seek(metaOutput.length())
          metaOutput.writeLong(timestamp)
          metaOutput.writeInt(nonce)
          metaOutput.writeLong(dataOffset)
          metaOutput.writeInt(bytes.length)
          metaOutput.write(dataSHA.bytes)
          HashedIndexedMeta(
            index = metaIndex,
            timestamp = timestamp,
            sha=dataSHA
          )
        }
      }
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  def count(): Try[Long] = {
    Try {
      metaFile.length() / metaEntrySize
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  override def lastUpdated(): Try[Option[Long]] = {
    Using(new RandomAccessFile(metaFile, "r")) { indexFile =>
      getIndexLastEntry(indexFile).map(_.timestamp)
    }
  }
}

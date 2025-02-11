package webecho.tools.hashedindexedstorage

import java.io.{File, FileOutputStream, ObjectOutputStream, RandomAccessFile}
import scala.io.Codec
import scala.util.{Try, Using}

trait HashedIndexedFileStorage {
  def list(reverseOrder: Boolean = false, epoch: Option[Long] = None): Try[Iterator[String]]
  def append(data: String): Try[HashedIndexedMeta]
  def lastUpdated(): Try[Option[Long]]
  def count(): Try[Long]
}

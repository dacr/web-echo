package webecho.tools

import scala.util.{Try, Using}
import java.io.{File, FileOutputStream, ObjectOutputStream, RandomAccessFile}
import scala.io.Codec

trait HashedIndexedFileStorage {
  def list(reverseOrder: Boolean = false, fromEpoch: Option[Long] = None): Try[Iterator[String]]
  def append(data: String): Try[SHA]
  def lastUpdated(): Try[Option[Long]]
  def count(): Try[Long]
}

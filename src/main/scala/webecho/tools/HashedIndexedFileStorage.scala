package webecho.tools

import scala.util.{Try, Using}
import java.io.{File, FileOutputStream, ObjectOutputStream, RandomAccessFile}
import scala.io.Codec

trait HashedIndexedFileStorage {
  def list(reverseOrder: Boolean = false, epoch: Option[Long] = None): Try[Iterator[String]]
  def listWithMeta(reverseOrder: Boolean = false, epoch: Option[Long] = None): Try[Iterator[(HashedIndexedMeta, String)]]
  def append(data: String): Try[HashedIndexedMeta]
  def lastUpdated(): Try[Option[Long]]
  def count(): Try[Long]
}

package webecho.tools

import scala.util.{Try, Using}
import java.io.{File, FileOutputStream, ObjectOutputStream, RandomAccessFile}
import scala.io.Codec

trait HashedIndexedFileStorage {
  def list(reverseOrder: Boolean = false, epoch: Option[Long] = None): Try[CloseableIterator[String]]
  def listWithMeta(reverseOrder: Boolean = false, epoch: Option[Long] = None): Try[CloseableIterator[(HashedIndexedMeta, String)]]
  def append(data: String): Try[HashedIndexedMeta]
  def updatedOn(): Try[Option[Long]]
  def last(): Try[Option[String]]
  def lastWithMeta(): Try[Option[(HashedIndexedMeta, String)]]
  def count(): Try[Long]
}

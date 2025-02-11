package webecho.tools.hashedindexedstorage

trait CloseableIterator[T] extends Iterator[T] with AutoCloseable {
  override def hasNext: Boolean
  override def next(): T
  override def close(): Unit
}

object CloseableIterator {
  def empty[T]: CloseableIterator[T] = new CloseableIterator[T] {
    override def hasNext: Boolean = false
    override def next(): T        = throw new NoSuchElementException("Iterator is empty")
    override def close(): Unit    = ()
  }
}

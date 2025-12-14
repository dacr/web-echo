package webecho.tools

trait CloseableIterator[T] extends Iterator[T] with AutoCloseable {
  self =>
  override def hasNext: Boolean
  override def next(): T
  override def close(): Unit
  override def map[B](f: T => B): CloseableIterator[B] = new CloseableIterator[B] {
    override def hasNext: Boolean = self.hasNext
    override def next(): B        = f(self.next())
    override def close(): Unit    = self.close()
  }
}

object CloseableIterator {
  def empty[T]: CloseableIterator[T] = new CloseableIterator[T] {
    override def hasNext: Boolean = false
    override def next(): T        = throw new NoSuchElementException("Iterator is empty")
    override def close(): Unit    = ()
  }
  
  def fromIterator[T](it: Iterator[T]): CloseableIterator[T] = new CloseableIterator[T] {
    override def hasNext: Boolean = it.hasNext
    override def next(): T        = it.next()
    override def close(): Unit    = ()
  }
}

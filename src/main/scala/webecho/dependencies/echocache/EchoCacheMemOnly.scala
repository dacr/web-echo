package webecho.dependencies.echocache
import org.json4s.JValue
import webecho.ServiceConfig

import java.util.UUID

object EchoCacheMemOnly{
  def apply(config:ServiceConfig) = new EchoCacheMemOnly(config)
}

case class CacheEntry(
  lastUpdated:Long,
  content:List[JValue]
)

// Just a naive and dangerous implementation
class EchoCacheMemOnly(config:ServiceConfig) extends EchoCache {

  private var cache = Map.empty[UUID, CacheEntry]

  def now(): Long = System.currentTimeMillis()

  override def entriesCount(): Int = cache.size

  override def deleteEntry(uuid: UUID) = {
    if (cache.contains(uuid)) {
      cache.synchronized {
        cache -= uuid
      }
    }
  }

  override def get(uuid: UUID): Option[CacheEntry] = {
    cache.get(uuid)
  }

  override def prepend(uuid: UUID, value: JValue): Unit = {
    cache.synchronized {
      cache.get(uuid) match {
        case None =>
        case Some(oldEntry) =>
          val newEntry = oldEntry.copy(lastUpdated = now(), content = value :: oldEntry.content)
          cache = cache.updated(uuid, newEntry)
      }
    }
  }

  override def createEntry(uuid: UUID): Unit = {
    cache.synchronized {
      cache += uuid -> CacheEntry(now(), Nil)
    }
  }

  override def hasEntry(uuid: UUID): Boolean = cache.contains(uuid)

  override def lastUpdated(): Option[Long] = cache.values.maxByOption(_.lastUpdated).map(_.lastUpdated)
}

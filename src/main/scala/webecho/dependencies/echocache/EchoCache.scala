package webecho.dependencies.echocache

import org.json4s.JValue
import java.util.UUID

trait EchoCache {
  def entriesCount(): Int
  def lastUpdated(): Option[Long]
  def hasEntry(uuid: UUID):Boolean
  def deleteEntry(uuid:UUID): Unit
  def createEntry(uuid:UUID): Unit
  def get(uuid: UUID): Option[CacheEntry]
  def prepend(uuid: UUID,value: JValue):Unit
}

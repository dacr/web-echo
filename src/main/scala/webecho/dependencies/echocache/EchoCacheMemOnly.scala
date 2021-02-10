/*
 * Copyright 2021 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package webecho.dependencies.echocache

import org.json4s.JValue
import webecho.ServiceConfig

import java.util.UUID

case class EchoCacheMemOnlyEntry(
  lastUpdated: Long,
  content: List[JValue]
)

object EchoCacheMemOnly {
  def apply(config: ServiceConfig) = new EchoCacheMemOnly(config)
}


// Just a naive and dangerous implementation !!!
// NOT FOR PRODUCTION

class EchoCacheMemOnly(config: ServiceConfig) extends EchoCache {

  private var cache = Map.empty[UUID, EchoCacheMemOnlyEntry]
  private var origins = Map.empty[UUID, EchoOrigin]

  def now(): Long = System.currentTimeMillis()

  override def entryDelete(uuid: UUID) = {
    if (cache.contains(uuid)) {
      cache.synchronized {
        cache -= uuid
      }
    }
    if (origins.contains(uuid)) {
      origins.synchronized {
        origins -= uuid
      }
    }
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

  override def entryCreate(uuid: UUID, origin:EchoOrigin): Unit = {
    cache.synchronized {
      cache += uuid -> EchoCacheMemOnlyEntry(now(), Nil)
    }
    origins.synchronized {
      origins += uuid -> origin
    }
  }

  override def entryExists(uuid: UUID): Boolean = cache.contains(uuid)

  override def entriesInfo(): Option[EchoesInfo] = {
    if (cache.size == 0) None else
      Some(EchoesInfo(
      lastUpdated = cache.values.maxBy(_.lastUpdated).lastUpdated,
      count = cache.size
    ))
  }

  override def entryInfo(uuid: UUID): Option[EchoInfo] = {
    cache.get(uuid).map(entry => EchoInfo(lastUpdated = entry.lastUpdated, count = entry.content.size, origin = origins.get(uuid)))
  }

  override def get(uuid: UUID): Option[Iterator[JValue]] = {
    cache.get(uuid).map(_.content.iterator)
  }
}

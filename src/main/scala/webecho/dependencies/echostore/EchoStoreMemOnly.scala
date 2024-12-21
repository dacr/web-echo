/*
 * Copyright 2020-2022 David Crosson
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
package webecho.dependencies.echostore

import org.json4s.JValue
import webecho.ServiceConfig
import webecho.model.{EchoInfo, EchoWebSocket, EchoesInfo, OperationOrigin}
import webecho.tools.{DateTimeTools, UniqueIdentifiers}

import java.time.Instant
import java.util.UUID

case class EchoCacheMemOnlyEntry(
  lastUpdated: Option[Instant],
  content: List[JValue],
  origin: Option[OperationOrigin]
)

object EchoStoreMemOnly extends DateTimeTools {
  def apply(config: ServiceConfig) = new EchoStoreMemOnly(config)
}

// Just a naive and dangerous implementation !!!
// NOT FOR PRODUCTION

class EchoStoreMemOnly(config: ServiceConfig) extends EchoStore with DateTimeTools {

  private var cache   = Map.empty[UUID, EchoCacheMemOnlyEntry]
  private var wsCache = Map.empty[UUID, Map[UUID, EchoWebSocket]]

  override def entriesList(): Iterable[UUID] = {
    cache.keys
  }

  override def entryDelete(uuid: UUID) = {
    cache.synchronized {
      if (cache.contains(uuid)) cache -= uuid
    }
    wsCache.synchronized {
      if (wsCache.contains(uuid)) wsCache -= uuid
    }
  }

  override def entryPrependValue(uuid: UUID, value: JValue): Unit = {
    cache.synchronized {
      cache.get(uuid) match {
        case None           =>
        case Some(oldEntry) =>
          val newEntry = oldEntry.copy(lastUpdated = Some(now()), content = value :: oldEntry.content)
          cache = cache.updated(uuid, newEntry)
      }
    }
  }

  override def entryAdd(uuid: UUID, origin: Option[OperationOrigin]): Unit = {
    cache.synchronized {
      cache += uuid -> EchoCacheMemOnlyEntry(Some(now()), Nil, origin)
    }
  }

  override def entryExists(uuid: UUID): Boolean = cache.contains(uuid)

  override def entriesInfo(): Option[EchoesInfo] = {
    if (cache.size == 0) None
    else
      Some(
        EchoesInfo(
          lastUpdated = cache.values.maxBy(_.lastUpdated).lastUpdated,
          count = cache.size
        )
      )
  }

  override def entryInfo(uuid: UUID): Option[EchoInfo] = {
    cache.get(uuid).map(entry => EchoInfo(lastUpdated = entry.lastUpdated, count = entry.content.size, origin = entry.origin))
  }

  override def entryGet(uuid: UUID): Option[Iterator[JValue]] = {
    cache.get(uuid).map(_.content.iterator)
  }

  override def webSocketAdd(entryUUID: UUID, uri: String, userData: Option[String], origin: Option[OperationOrigin]): EchoWebSocket = {
    val uuid          = UniqueIdentifiers.timedUUID()
    val echoWebSocket = EchoWebSocket(
      uuid,
      uri,
      userData,
      origin
    )
    wsCache.synchronized {
      wsCache += entryUUID -> (wsCache.getOrElse(entryUUID, Map.empty) + (uuid -> echoWebSocket))
    }
    echoWebSocket
  }

  override def webSocketGet(entryUUID: UUID, uuid: UUID): Option[EchoWebSocket] = {
    wsCache.getOrElse(entryUUID, Map.empty).get(uuid)
  }

  override def webSocketDelete(entryUUID: UUID, uuid: UUID): Option[Boolean] = {
    wsCache.synchronized {
      if (wsCache.contains(entryUUID)) {
        wsCache += entryUUID -> (wsCache.getOrElse(entryUUID, Map.empty) - uuid)
        Some(true)
      } else None
    }
  }

  override def webSocketList(entryUUID: UUID): Option[Iterable[EchoWebSocket]] = {
    wsCache.get(entryUUID).map(_.values)
  }

}

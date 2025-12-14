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

import webecho.ServiceConfig
import webecho.model.{ReceiptProof, EchoInfo, WebSocket, StoreInfo, Origin}
import webecho.tools.{DateTimeTools, JsonSupport, SHA256Engine, UniqueIdentifiers}
import com.github.plokhotnyuk.jsoniter_scala.core._

import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import scala.util.{Failure, Success, Try}

case class EchoCacheMemOnlyEntry(
  lastUpdated: Option[Instant],
  content: List[Any],
  origin: Option[Origin]
)

object EchoStoreMemOnly extends DateTimeTools {
  def apply(config: ServiceConfig) = new EchoStoreMemOnly(config)
}

// Just a naive and dangerous implementation !!!
// NOT FOR PRODUCTION

class EchoStoreMemOnly(config: ServiceConfig) extends EchoStore with DateTimeTools with JsonSupport {

  private var cache   = Map.empty[UUID, EchoCacheMemOnlyEntry]
  private var wsCache = Map.empty[UUID, Map[UUID, WebSocket]]

  override def storeList(): Iterable[UUID] = {
    cache.keys
  }

  override def echoDelete(id: UUID) = {
    cache.synchronized {
      if (cache.contains(id)) cache -= id
    }
    wsCache.synchronized {
      if (wsCache.contains(id)) wsCache -= id
    }
  }

  override def echoAddContent(id: UUID, content: Any): Try[ReceiptProof] = {
    cache.synchronized {
      cache.get(id) match {
        case None           => Failure(new RuntimeException(s"Unable to find echo $id"))
        case Some(oldEntry) =>
          val newEntry = oldEntry.copy(lastUpdated = Some(now()), content = content :: oldEntry.content)
          cache = cache.updated(id, newEntry)
          Success(
            ReceiptProof(
              index = cache.size,
              timestamp = newEntry.lastUpdated.map(_.toEpochMilli).getOrElse(0L),
              sha256 = SHA256Engine.digest(newEntry.toString.getBytes("UTF-8")).toString
            )
          )
      }
    }
  }

  override def echoAdd(id: UUID, origin: Option[Origin]): Unit = {
    cache.synchronized {
      cache += id -> EchoCacheMemOnlyEntry(Some(now()), Nil, origin)
    }
  }

  override def echoExists(id: UUID): Boolean = cache.contains(id)

  override def storeInfo(): Option[StoreInfo] = {
    if (cache.size == 0) None
    else
      Some(
        StoreInfo(
          lastUpdated = cache.values.maxBy(_.lastUpdated).lastUpdated,
          count = cache.size
        )
      )
  }

  override def echoInfo(id: UUID): Option[EchoInfo] = {
    cache.get(id).map(entry => EchoInfo(lastUpdated = entry.lastUpdated, count = entry.content.size, origin = entry.origin))
  }

  override def echoGet(id: UUID): Option[Iterator[String]] = {
    cache.get(id).map(_.content.iterator.map(v => writeToString(v)))
  }

  override def webSocketAdd(echoId: UUID, uri: String, userData: Option[String], origin: Option[Origin], expiresAt: Option[OffsetDateTime]): WebSocket = {
    val uuid          = UniqueIdentifiers.timedUUID()
    val echoWebSocket = WebSocket(
      uuid,
      uri,
      userData,
      origin,
      expiresAt
    )
    wsCache.synchronized {
      wsCache += echoId -> (wsCache.getOrElse(echoId, Map.empty) + (uuid -> echoWebSocket))
    }
    echoWebSocket
  }

  override def webSocketGet(echoId: UUID, id: UUID): Option[WebSocket] = {
    wsCache.getOrElse(echoId, Map.empty).get(id)
  }

  override def webSocketDelete(echoId: UUID, id: UUID): Option[Boolean] = {
    wsCache.synchronized {
      if (wsCache.contains(echoId)) {
        wsCache += echoId -> (wsCache.getOrElse(echoId, Map.empty) - id)
        Some(true)
      } else None
    }
  }

  override def webSocketList(echoId: UUID): Option[Iterable[WebSocket]] = {
    wsCache.get(echoId).map(_.values)
  }

}

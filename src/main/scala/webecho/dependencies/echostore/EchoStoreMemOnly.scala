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
import webecho.model.{ReceiptProof, EchoInfo, WebSocket, StoreInfo, Origin, Record}
import webecho.tools.{CloseableIterator, DateTimeTools, SHA256Engine, UniqueIdentifiers}
import webecho.tools.JsonSupport.given
import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.Duration

case class EchoCacheMemOnlyEntry(
  updatedOn: Option[Instant],
  content: List[(ReceiptProof, Any)],
  origin: Option[Origin],
  description: Option[String] = None,
  lifeExpectancy: Option[Duration] = None
)

object EchoStoreMemOnly extends DateTimeTools {
  def apply(config: ServiceConfig) = new EchoStoreMemOnly(config)
}

// Just a naive and dangerous implementation !!!
// NOT FOR PRODUCTION

class EchoStoreMemOnly(config: ServiceConfig) extends EchoStore with DateTimeTools {
  import scala.concurrent.duration.Duration

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
          val proof    = ReceiptProof(
            index = oldEntry.content.size + 1L,
            timestamp = now().toEpochMilli,
            nonce = 0,
            sha256 = SHA256Engine.digest(content.toString.getBytes("UTF-8")).toString
          )
          val newEntry = oldEntry.copy(updatedOn = Some(Instant.ofEpochMilli(proof.timestamp)), content = (proof, content) :: oldEntry.content)
          cache = cache.updated(id, newEntry)
          Success(proof)
      }
    }
  }

  override def echoAdd(id: UUID, description:Option[String], origin: Option[Origin], lifeExpectancy: Option[Duration]): Unit = {
    cache.synchronized {
      cache += id -> EchoCacheMemOnlyEntry(Some(now()), Nil, origin, description, lifeExpectancy)
    }
  }

  override def echoUpdate(id: UUID, description: Option[String], lifeExpectancy: Option[Duration]): Unit = {
    cache.synchronized {
      cache.get(id).foreach { entry =>
        cache += id -> entry.copy(description = description, lifeExpectancy = lifeExpectancy)
      }
    }
  }

  override def echoExists(id: UUID): Boolean = cache.contains(id)

  override def storeInfo(): Option[StoreInfo] = {
    if (cache.size == 0) None
    else
      Some(
        StoreInfo(
          lastUpdated = cache.values.maxBy(_.updatedOn).updatedOn,
          count = cache.size
        )
      )
  }

  override def echoInfo(id: UUID): Option[EchoInfo] = {
    cache.get(id).map(entry => EchoInfo(description = entry.description, updatedOn = entry.updatedOn, count = entry.content.size, origin = entry.origin, lifeExpectancy = entry.lifeExpectancy))
  }

  override def echoGet(id: UUID): Option[CloseableIterator[Record]] = {
    cache.get(id).map(entry => CloseableIterator.fromIterator(entry.content.iterator).map { case (_, v) => readFromString[Record](writeToString(v)) })
  }

  override def echoGetWithProof(id: UUID): Option[CloseableIterator[(ReceiptProof, Record)]] = {
    cache.get(id).map(entry => CloseableIterator.fromIterator(entry.content.iterator).map { case (proof, v) => (proof, readFromString[Record](writeToString(v))) })
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

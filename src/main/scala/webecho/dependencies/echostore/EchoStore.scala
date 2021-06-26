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
package webecho.dependencies.echostore

import org.json4s.JValue
import webecho.model.{EchoInfo, EchoWebSocket, EchoesInfo, OperationOrigin}

import java.util.UUID

trait EchoStore {
  def entriesInfo(): Option[EchoesInfo]

  def entriesList(): Iterable[UUID]

  def entryInfo(uuid: UUID): Option[EchoInfo]

  def entryExists(uuid: UUID): Boolean

  def entryDelete(uuid: UUID): Unit

  def entryAdd(uuid: UUID, origin: Option[OperationOrigin]): Unit

  def entryGet(uuid: UUID): Option[Iterator[JValue]]

  def entryPrependValue(uuid: UUID, value: JValue): Unit

  def webSocketAdd(entryUUID: UUID, uri: String, userData: Option[String], origin: Option[OperationOrigin]): EchoWebSocket

  def webSocketGet(entryUUID: UUID, uuid: UUID): Option[EchoWebSocket]

  def webSocketDelete(entryUUID: UUID, uuid: UUID): Option[Boolean]

  def webSocketList(entryUUID: UUID): Option[Iterable[EchoWebSocket]]
}

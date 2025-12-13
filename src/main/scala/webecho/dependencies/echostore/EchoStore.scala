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

import webecho.model.{ReceiptProof, EchoInfo, WebSocket, StoreInfo, Origin}

import java.util.UUID
import scala.util.Try

trait EchoStore {
  def storeInfo(): Option[StoreInfo]

  def storeList(): Iterable[UUID]

  def echoInfo(id: UUID): Option[EchoInfo]

  def echoExists(id: UUID): Boolean

  def echoDelete(id: UUID): Unit

  def echoAdd(id: UUID, origin: Option[Origin]): Unit

  def echoGet(id: UUID): Option[Iterator[String]]

  def echoAddValue(id: UUID, value: Any): Try[ReceiptProof]

  def webSocketAdd(echoId: UUID, uri: String, userData: Option[String], origin: Option[Origin]): WebSocket

  def webSocketGet(echoId: UUID, id: UUID): Option[WebSocket]

  def webSocketDelete(echoId: UUID, id: UUID): Option[Boolean]

  def webSocketList(echoId: UUID): Option[Iterable[WebSocket]]
}

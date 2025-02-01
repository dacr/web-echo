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
import webecho.model.{EchoAddedMeta, EchoInfo, EchoWebSocket, EchoesInfo, OperationOrigin}

import java.util.UUID
import scala.util.Try

trait EchoStore {
  def echoesInfo(): Option[EchoesInfo]

  def echoesList(): Iterable[UUID]

  def echoInfo(uuid: UUID): Option[EchoInfo]

  def echoExists(uuid: UUID): Boolean

  def echoDelete(uuid: UUID): Unit

  def echoAdd(uuid: UUID, origin: Option[OperationOrigin]): Unit

  def echoGet(uuid: UUID): Option[Iterator[JValue]]

  def echoAddValue(uuid: UUID, value: JValue): Try[EchoAddedMeta]

  def webSocketAdd(echoUUID: UUID, uri: String, userData: Option[String], origin: Option[OperationOrigin]): EchoWebSocket

  def webSocketGet(echoUUID: UUID, uuid: UUID): Option[EchoWebSocket]

  def webSocketDelete(echoUUID: UUID, uuid: UUID): Option[Boolean]

  def webSocketList(echoUUID: UUID): Option[Iterable[EchoWebSocket]]
}

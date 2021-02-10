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
import java.util.UUID

case class EchoInfo(
  lastUpdated: Long,
  count: Long
)


trait EchoCache {
  def entriesInfo(): Option[EchoInfo]

  def entryInfo(uuid: UUID): Option[EchoInfo]

  def entryExists(uuid: UUID): Boolean

  def entryDelete(uuid: UUID): Unit

  def entryCreate(uuid: UUID): Unit

  def get(uuid: UUID): Option[Iterator[JValue]]

  def prepend(uuid: UUID, value: JValue): Unit
}

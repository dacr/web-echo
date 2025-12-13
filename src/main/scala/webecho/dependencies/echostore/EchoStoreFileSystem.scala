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

import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import webecho.ServiceConfig
import webecho.model.{Echo, EchoInfo, Origin, ReceiptProof, StoreInfo, WebSocket}
import webecho.tools.{HashedIndexedFileStorageLive, JsonSupport, UniqueIdentifiers}
import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.io.{File, FileFilter, FilenameFilter}
import java.time.Instant
import java.util.UUID
import scala.util.Try

object EchoStoreFileSystem {
  def apply(config: ServiceConfig) = new EchoStoreFileSystem(config)
}

// TODO not absolutely "thread safe" move to an actor based implementation
// But not so bad as everything is based on distinct files... => immutable file content ;)
class EchoStoreFileSystem(config: ServiceConfig) extends EchoStore with JsonSupport {
  private val logger             = LoggerFactory.getLogger(getClass)
  private val storeConfig        = config.webEcho.behavior.fileSystemCache
  private val storeBaseDirectory = {
    val path = new File(storeConfig.path)
    if (!path.exists()) {
      logger.info(s"Creating base directory $path")
      if (path.mkdirs()) logger.info(s"base directory $path created")
      else {
        val message = s"Unable to create base directory $path"
        logger.error(message)
        throw new RuntimeException(message)
      }
    }
    logger.info(s"Using $path to store echo data")
    path
  }

  private def fsEntries(): Option[Array[File]] = {
    val filter = new FileFilter {
      override def accept(file: File): Boolean = file.isDirectory
    }
    Option(storeBaseDirectory.listFiles(filter))
  }

  private def fsEntryBaseDirectory(uuid: UUID): File = {
    new File(storeBaseDirectory, uuid.toString)
  }

  private def fsEntryInfo(uuid: UUID): File = {
    new File(fsEntryBaseDirectory(uuid), "about")
  }

  private def fsEntryFiles(uuid: UUID): Option[Array[File]] = {
    val entryFilter = new FilenameFilter {
      override def accept(dir: File, name: String): Boolean =
        name.endsWith(".data") || name.endsWith(".meta")
    }
    Option(fsEntryBaseDirectory(uuid).listFiles(entryFilter))
  }

  private def fsEntryUUIDs(): Iterable[UUID] = {
    fsEntries()
      .getOrElse(Array.empty[File])
      .toList
      .map(_.getName)
      .flatMap(name => UniqueIdentifiers.fromString(name).toOption)
  }

  private def fsEntryWebSocketsFiles(uuid: UUID): Option[Array[File]] = {
    val filter = new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".wsjson")
    }
    Option(fsEntryBaseDirectory(uuid).listFiles(filter))
  }

  def jsonRead[T](file: File)(implicit codec: JsonValueCodec[T]): T = {
    readFromString(FileUtils.readFileToString(file, "UTF-8"))
  }

  def jsonWrite[T](file: File, value: T)(implicit codec: JsonValueCodec[T]) = {
    val tmpFile = new File(file.getParent, file.getName + ".tmp")
    FileUtils.write(tmpFile, writeToString(value), "UTF-8")
    tmpFile.renameTo(file)
  }

  // ===================================================================================================================

  override def storeInfo(): Option[StoreInfo] = {
    fsEntries() match {
      case None        => None
      case Some(files) =>
        Some(StoreInfo(count = files.length, lastUpdated = None)) // TODO lastUpdated
    }
  }

  override def echoInfo(id: UUID): Option[EchoInfo] = {
    val dest = fsEntryBaseDirectory(id)
    // TODO add caching to avoid systematic allocation
    // TODO switch to effect system to take into account the Try
    HashedIndexedFileStorageLive(dest.getAbsolutePath).toOption.map { storage =>
      val echo = Try(jsonRead[Echo](fsEntryInfo(id))).toOption
      EchoInfo(
        count = storage.count().toOption.getOrElse(0),
        lastUpdated = storage
          .lastUpdated()
          .toOption
          .flatten
          .map(Instant.ofEpochMilli),
        origin = echo.flatMap(_.origin)
      )
    }
  }

  override def storeList(): Iterable[UUID] = {
    fsEntryUUIDs()
  }

  override def echoExists(id: UUID): Boolean = fsEntryBaseDirectory(id).exists()

  override def echoDelete(id: UUID): Unit = {
    for {
      files <- List(Array(fsEntryInfo(id))) ++ fsEntryFiles(id) ++ fsEntryWebSocketsFiles(id)
      file  <- files
    } {
      file.delete() match {
        case true  =>
        case false => logger.warn(s"Was unable to delete file $file")
      }
    }

    val entryDir = fsEntryBaseDirectory(id)
    entryDir.delete() match {
      case true  =>
      case false => logger.warn(s"Was unable to delete directory $entryDir")
    }
  }

  override def echoAdd(id: UUID, origin: Option[Origin]): Unit = {
    val dest    = fsEntryBaseDirectory(id)
    // TODO add caching to avoid systematic allocation
    // TODO switch to effect system to take into account the Try
    val storage = HashedIndexedFileStorageLive(dest.getAbsolutePath).get
    jsonWrite(fsEntryInfo(id), Echo(id = id, origin = origin))
  }

  override def echoGet(id: UUID): Option[Iterator[String]] = {
    val dest = fsEntryBaseDirectory(id)
    if (!dest.exists()) None
    else {
      // TODO add caching to avoid systematic allocation
      // TODO switch to effect system to take into account the Try
      HashedIndexedFileStorageLive(dest.getAbsolutePath).toOption
        .map { storage =>
          storage
            .list(reverseOrder = true)
            .get
        }
    }
  }

  override def echoAddValue(id: UUID, value: Any): Try[ReceiptProof] = {
    val dest = fsEntryBaseDirectory(id)
    // TODO add caching to avoid systematic allocation
    // TODO switch to effect system to take into account the Try
    HashedIndexedFileStorageLive(dest.getAbsolutePath).flatMap { storage =>
      storage
        .append(writeToString(value))
        .map(result =>
          ReceiptProof(
            index = result.index,
            timestamp = result.timestamp,
            sha256 = result.sha.toString
          )
        )
    }
  }

  private def makeWebSocketJsonFile(entryUUID: UUID, uuid: UUID) = {
    val baseDir = fsEntryBaseDirectory(entryUUID)
    new File(baseDir, s"$uuid.wsjson")
  }

  override def webSocketAdd(echoId: UUID, uri: String, userData: Option[String], origin: Option[Origin]): WebSocket = {
    val uuid          = UniqueIdentifiers.timedUUID()
    val echoWebSocket = WebSocket(
      uuid,
      uri,
      userData,
      origin
    )
    val jsonFile      = makeWebSocketJsonFile(echoId, uuid)
    jsonWrite(jsonFile, echoWebSocket)
    echoWebSocket
  }

  override def webSocketGet(echoId: UUID, id: UUID): Option[WebSocket] = {
    val jsonFile = makeWebSocketJsonFile(echoId, id)
    if (!jsonFile.exists()) None
    else {
      Try(jsonRead[WebSocket](jsonFile)).toOption
    }
  }

  override def webSocketDelete(echoId: UUID, id: UUID): Option[Boolean] = {
    val jsonFile = makeWebSocketJsonFile(echoId, id)
    if (!jsonFile.exists()) None
    else {
      Some(jsonFile.delete())
    }
  }

  override def webSocketList(echoId: UUID): Option[Iterable[WebSocket]] = {
    fsEntryWebSocketsFiles(echoId).map { files =>
      files
        .to(Iterable)
        .map(file => Try(jsonRead[WebSocket](file)).toOption)
        .flatten
    }
  }

}

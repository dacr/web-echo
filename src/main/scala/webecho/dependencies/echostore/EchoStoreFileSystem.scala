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
import org.json4s.*
import org.json4s.Extraction.decompose
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import org.slf4j.LoggerFactory
import webecho.ServiceConfig
import webecho.model.{EchoAddedMeta, EchoInfo, EchoWebSocket, EchoesInfo, OperationOrigin}
import webecho.tools.{HashedIndexedFileStorageLive, JsonImplicits, UniqueIdentifiers}

import java.io.{File, FileFilter, FilenameFilter}
import java.time.Instant
import java.util.UUID
import scala.util.Try

object EchoStoreFileSystem {
  def apply(config: ServiceConfig) = new EchoStoreFileSystem(config)
}

// TODO not absolutely "thread safe" move to an actor based implementation
// But not so bad as everything is based on distinct files... => immutable file content ;)
class EchoStoreFileSystem(config: ServiceConfig) extends EchoStore with JsonImplicits {
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

  def jsonRead(file: File): JValue = {
    parse(FileUtils.readFileToString(file, "UTF-8"))
  }

  def jsonWrite(file: File, value: JValue) = {
    val tmpFile = new File(file.getParent, file.getName + ".tmp")
    FileUtils.write(tmpFile, write(value), "UTF-8")
    tmpFile.renameTo(file)
  }

  // ===================================================================================================================

  override def echoesInfo(): Option[EchoesInfo] = {
    fsEntries() match {
      case None        => None
      case Some(files) =>
        Some(EchoesInfo(count = files.length, lastUpdated = None)) // TODO lastUpdated
    }
  }

  override def echoInfo(uuid: UUID): Option[EchoInfo] = {
    val dest = fsEntryBaseDirectory(uuid)
    // TODO add caching to avoid systematic allocation
    // TODO switch to effect system to take into account the Try
    HashedIndexedFileStorageLive(dest.getAbsolutePath).toOption.map { storage =>
      val origin = jsonRead(fsEntryInfo(uuid)).extractOpt[OperationOrigin]
      EchoInfo(
        count = storage.count().toOption.getOrElse(0),
        lastUpdated = storage
          .lastUpdated()
          .toOption
          .flatten
          .map(Instant.ofEpochMilli),
        origin = origin
      )
    }
  }

  override def echoesList(): Iterable[UUID] = {
    fsEntryUUIDs()
  }

  override def echoExists(uuid: UUID): Boolean = fsEntryBaseDirectory(uuid).exists()

  override def echoDelete(uuid: UUID): Unit = {
    for {
      files <- List(Array(fsEntryInfo(uuid))) ++ fsEntryFiles(uuid) ++ fsEntryWebSocketsFiles(uuid)
      file  <- files
    } {
      file.delete() match {
        case true  =>
        case false => logger.warn(s"Was unable to delete file $file")
      }
    }

    val entryDir = fsEntryBaseDirectory(uuid)
    entryDir.delete() match {
      case true  =>
      case false => logger.warn(s"Was unable to delete directory $entryDir")
    }
  }

  override def echoAdd(uuid: UUID, origin: Option[OperationOrigin]): Unit = {
    val dest    = fsEntryBaseDirectory(uuid)
    // TODO add caching to avoid systematic allocation
    // TODO switch to effect system to take into account the Try
    val storage = HashedIndexedFileStorageLive(dest.getAbsolutePath).get
    jsonWrite(fsEntryInfo(uuid), decompose(origin))
  }

  override def echoGet(uuid: UUID): Option[Iterator[JValue]] = {
    val dest = fsEntryBaseDirectory(uuid)
    if (!dest.exists()) None
    else {
      // TODO add caching to avoid systematic allocation
      // TODO switch to effect system to take into account the Try
      HashedIndexedFileStorageLive(dest.getAbsolutePath).toOption
        .map { storage =>
          storage
            .list(reverseOrder = true)
            .get
            .map(entry => parse(entry))
        }
    }
  }

  override def echoAddValue(uuid: UUID, value: JValue): Try[EchoAddedMeta] = {
    val dest = fsEntryBaseDirectory(uuid)
    // TODO add caching to avoid systematic allocation
    // TODO switch to effect system to take into account the Try
    HashedIndexedFileStorageLive(dest.getAbsolutePath).flatMap { storage =>
      storage
        .append(write(value))
        .map(result =>
          EchoAddedMeta(
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

  override def webSocketAdd(echoUUID: UUID, uri: String, userData: Option[String], origin: Option[OperationOrigin]): EchoWebSocket = {
    val uuid          = UniqueIdentifiers.timedUUID()
    val echoWebSocket = EchoWebSocket(
      uuid,
      uri,
      userData,
      origin
    )
    val jsonFile      = makeWebSocketJsonFile(echoUUID, uuid)
    jsonWrite(jsonFile, decompose(echoWebSocket))
    echoWebSocket
  }

  override def webSocketGet(echoUUID: UUID, uuid: UUID): Option[EchoWebSocket] = {
    val jsonFile = makeWebSocketJsonFile(echoUUID, uuid)
    if (!jsonFile.exists()) None
    else {
      jsonRead(jsonFile).extractOpt[EchoWebSocket]
    }
  }

  override def webSocketDelete(echoUUID: UUID, uuid: UUID): Option[Boolean] = {
    val jsonFile = makeWebSocketJsonFile(echoUUID, uuid)
    if (!jsonFile.exists()) None
    else {
      Some(jsonFile.delete())
    }
  }

  override def webSocketList(echoUUID: UUID): Option[Iterable[EchoWebSocket]] = {
    fsEntryWebSocketsFiles(echoUUID).map { files =>
      files
        .to(Iterable)
        .map(jsonRead)
        .flatMap(_.extractOpt[EchoWebSocket])
    }
  }

}

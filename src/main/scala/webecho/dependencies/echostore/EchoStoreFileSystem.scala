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
import webecho.model.{EchoAddedMeta, EchoInfo, EchoWebSocket, EchoesInfo, Origin}
import webecho.tools.{HashedIndexedFileStorageLive, JsonSupport, UniqueIdentifiers}
import com.github.plokhotnyuk.jsoniter_scala.core._

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
      val origin = Try(jsonRead[Origin](fsEntryInfo(uuid))).toOption
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

  override def echoAdd(uuid: UUID, origin: Option[Origin]): Unit = {
    val dest    = fsEntryBaseDirectory(uuid)
    // TODO add caching to avoid systematic allocation
    // TODO switch to effect system to take into account the Try
    val storage = HashedIndexedFileStorageLive(dest.getAbsolutePath).get
    // origin can be None, but jsoniter needs explicit handling if Origin is optional? 
    // Wait, the signature of jsonWrite is T. If I pass Option[Origin], I need codec for Option[Origin].
    // origin is Option[Origin].
    // json4s decompose(origin) handles Option automatically.
    // I need to decide if I write "null" or the object.
    // If I pass origin directly, T is Option[Origin].
    // Implicit codec for Option[Origin] is needed. JsonCodecMaker supports it but I need to ensure it's available.
    // Or just write Origin if defined?
    // The previous code `decompose(origin)` produced `JNull` or `JObject`.
    // I'll assume I can write Option[Origin].
    // However, jsonRead reads `Origin`. If it was JNull, extractOpt[Origin] would work.
    // If I write "null" (JSON null), readFromString[Origin] might fail if Origin is not nullable?
    // Actually, it's better to verify if I should write Option.
    // Let's rely on standard serialization of Option.
    // But I need implicit codec for Option[Origin]. `JsonSupport` only has `originCodec`.
    // I can define `implicit val optOriginCodec: JsonValueCodec[Option[Origin]] = JsonCodecMaker.make` in JsonSupport
    // Or locally.
    // For now, I will use: if(origin.isDefined) jsonWrite(..., origin.get) else ...?
    // But then reading back expects a file.
    // Let's assume origin is always written.
    // I will add `implicit val optOriginCodec: JsonValueCodec[Option[Origin]] = JsonCodecMaker.make` to JsonSupport later or rely on derived?
    // No, I must be explicit.
    // Actually, `origin` field in `EchoInfo` is `Option[Origin]`.
    // `echoAdd` takes `Option[Origin]`.
    // `echoInfo` logic: `Try(jsonRead[Origin](...)).toOption`. 
    // If the file contains "null", `readFromString[Origin]("null")` throws?
    // Yes, unless Origin is a case class, it expects object.
    // So I should read `Option[Origin]`.
    // I will change `echoInfo` to read `Option[Origin]`.
    
    // For now, let's just write/read `Option[Origin]`.
    // I will add `implicit val optOriginCodec: JsonValueCodec[Option[Origin]] = JsonCodecMaker.make` to `JsonSupport`.
    
    jsonWrite(fsEntryInfo(uuid), origin)
  }

  override def echoGet(uuid: UUID): Option[Iterator[String]] = {
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
        }
    }
  }

  override def echoAddValue(uuid: UUID, value: Any): Try[EchoAddedMeta] = {
    val dest = fsEntryBaseDirectory(uuid)
    // TODO add caching to avoid systematic allocation
    // TODO switch to effect system to take into account the Try
    HashedIndexedFileStorageLive(dest.getAbsolutePath).flatMap { storage =>
      storage
        .append(writeToString(value))
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

  override def webSocketAdd(echoUUID: UUID, uri: String, userData: Option[String], origin: Option[Origin]): EchoWebSocket = {
    val uuid          = UniqueIdentifiers.timedUUID()
    val echoWebSocket = EchoWebSocket(
      uuid,
      uri,
      userData,
      origin
    )
    val jsonFile      = makeWebSocketJsonFile(echoUUID, uuid)
    jsonWrite(jsonFile, echoWebSocket)
    echoWebSocket
  }

  override def webSocketGet(echoUUID: UUID, uuid: UUID): Option[EchoWebSocket] = {
    val jsonFile = makeWebSocketJsonFile(echoUUID, uuid)
    if (!jsonFile.exists()) None
    else {
      Try(jsonRead[EchoWebSocket](jsonFile)).toOption
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
        .map(file => Try(jsonRead[EchoWebSocket](file)).toOption)
        .flatten
    }
  }

}

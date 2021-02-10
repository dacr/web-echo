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

import org.apache.commons.io.FileUtils
import org.json4s.Extraction.decompose
import org.json4s.JValue
import org.slf4j.LoggerFactory
import org.json4s.jackson.Serialization.write
import org.json4s.jackson.JsonMethods.parse
import webecho.ServiceConfig
import webecho.tools.JsonImplicits

import java.io.{File, FileFilter, FilenameFilter}
import java.util.UUID

object EchoCacheFileSystem {
  def apply(config: ServiceConfig) = new EchoCacheFileSystem(config)
}

class EchoCacheFileSystem(config:ServiceConfig) extends EchoCache with JsonImplicits {
  private val logger = LoggerFactory.getLogger(getClass)
  private val cacheConfig = config.webEcho.behavior.fileSystemCache
  private val cacheBaseDirectory = {
    val path = new File(cacheConfig.path)
    if (!path.exists()) {
      logger.info(s"Creating base directory $path")
      if (path.mkdirs()) logger.info(s"base directory $path created")
      else {
        val message = s"unable to create base directory $path"
        logger.error(message)
        throw new RuntimeException(message)
      }
    }
    path
  }

  private def fsEntries():Option[Array[File]] = {
    val filter = new FileFilter {
      override def accept(file: File): Boolean = file.isDirectory
    }
    Option(cacheBaseDirectory.listFiles(filter))
  }

  private def fsEntryBaseDirectory(uuid: UUID):File = {
    new File(cacheBaseDirectory, uuid.toString)
  }

  private def fsEntryInfo(uuid:UUID):File = {
    new File(fsEntryBaseDirectory(uuid), "about")
  }

  private def fsEntryFiles(uuid:UUID):Option[Array[File]] = {
    val filter = new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".json")
    }
    def encodedFileCreatedTimestamp(file:File):Long = {
      file.getName.split("-", 2).head.toLongOption.getOrElse(0L)
    }
    Option(fsEntryBaseDirectory(uuid).listFiles(filter)).map(_.sortBy(f => -encodedFileCreatedTimestamp(f)))
  }

  def jsonRead(file:File):JValue = {
    parse(FileUtils.readFileToString(file, "UTF-8"))
  }

  def jsonWrite(file:File, value:JValue) = {
    val tmpFile=new File(file.getParent, file.getName+".tmp")
    FileUtils.write(tmpFile, write(value), "UTF-8")
    tmpFile.renameTo(file)
  }

  // ===================================================================================================================

  override def entriesInfo(): Option[EchoesInfo] = {
    fsEntries() match {
      case None => None
      case Some(files) =>
        Some(EchoesInfo(count=files.length, lastUpdated = 0L)) // TODO lastUpdated
    }
  }

  override def entryInfo(uuid: UUID): Option[EchoInfo] = {
    fsEntryFiles(uuid).map { files =>
      val origin = jsonRead(fsEntryInfo(uuid)).extractOpt[EchoOrigin]
      EchoInfo(
        count=files.length,
        lastUpdated = files.map(_.lastModified()).maxOption.getOrElse(0L),
        origin = origin
      )
    }
  }

  override def entryExists(uuid: UUID): Boolean = fsEntryBaseDirectory(uuid).exists()

  override def entryDelete(uuid: UUID): Unit = () // TODO to implement

  override def entryCreate(uuid: UUID, origin:EchoOrigin): Unit = {
    val dest = fsEntryBaseDirectory(uuid)
    dest.mkdir()
    jsonWrite(fsEntryInfo(uuid), decompose(origin))
  }

  override def get(uuid: UUID): Option[Iterator[JValue]] = {
    fsEntryFiles(uuid).map { files =>
      files
        .to(Iterator)
        .map(jsonRead)
    }
  }

  override def prepend(uuid: UUID, value: JValue): Unit = {
    val dest = fsEntryBaseDirectory(uuid)
    val ts = System.currentTimeMillis()
    val fileUUID = UUID.randomUUID().toString
    val jsonFile = new File(dest, s"$ts-$fileUUID.json")
    jsonWrite(jsonFile, value)
  }
}

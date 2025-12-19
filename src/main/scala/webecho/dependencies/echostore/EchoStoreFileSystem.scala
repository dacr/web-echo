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
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Terminated, Scheduler}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.util.Timeout
import org.slf4j.LoggerFactory
import webecho.ServiceConfig
import webecho.model.{Echo, EchoInfo, Origin, ReceiptProof, Record, StoreInfo, WebSocket}
import webecho.tools.{CloseableIterator, HashedIndexedFileStorage, HashedIndexedFileStorageLive, JsonSupport, SHAGoal, UniqueIdentifiers}
import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.io.{File, FileFilter, FilenameFilter}
import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

object EchoStoreFileSystem {
  def apply(config: ServiceConfig) = new EchoStoreFileSystem(config)

  // -- Actor Protocol --
  sealed trait Command
  case class GetStoreInfo(replyTo: ActorRef[Option[StoreInfo]]) extends Command
  case class GetStoreList(replyTo: ActorRef[Iterable[UUID]]) extends Command
  
  case class GetEchoInfo(id: UUID, replyTo: ActorRef[Option[EchoInfo]]) extends Command
  case class CreateEcho(id: UUID, origin: Option[Origin], replyTo: ActorRef[Unit]) extends Command
  case class DeleteEcho(id: UUID, replyTo: ActorRef[Unit]) extends Command
  case class CheckEchoExists(id: UUID, replyTo: ActorRef[Boolean]) extends Command
  
  case class GetEchoContent(id: UUID, replyTo: ActorRef[Option[CloseableIterator[Record]]]) extends Command
  case class GetEchoContentWithProof(id: UUID, replyTo: ActorRef[Option[CloseableIterator[(ReceiptProof, Record)]]]) extends Command
  case class AddEchoContent(id: UUID, content: Any, replyTo: ActorRef[Try[ReceiptProof]]) extends Command

  case class AddWebSocket(echoId: UUID, uri: String, userData: Option[String], origin: Option[Origin], expiresAt: Option[OffsetDateTime], replyTo: ActorRef[WebSocket]) extends Command
  case class GetWebSocket(echoId: UUID, id: UUID, replyTo: ActorRef[Option[WebSocket]]) extends Command
  case class DeleteWebSocket(echoId: UUID, id: UUID, replyTo: ActorRef[Option[Boolean]]) extends Command
  case class ListWebSockets(echoId: UUID, replyTo: ActorRef[Option[Iterable[WebSocket]]]) extends Command
  
  private case object ReceiveTimeout extends Command
}

class EchoStoreFileSystem(config: ServiceConfig) extends EchoStore with JsonSupport {
  import EchoStoreFileSystem.*

  private val logger             = LoggerFactory.getLogger(getClass)
  private val storeConfig        = config.webEcho.behavior.fileSystemCache
  private val shaGoal            = if (config.webEcho.behavior.shaGoal > 0) Some(SHAGoal.standard(config.webEcho.behavior.shaGoal)) else None
  
  private val storeBaseDirectory = {
    val path = new File(storeConfig.path)
    if (!path.exists()) {
      path.mkdirs()
    }
    path
  }

  // -- File System Helpers --
  private def fsEntries(): Option[Array[File]] = {
    val filter = new FileFilter {
      override def accept(file: File): Boolean = file.isDirectory
    }
    Option(storeBaseDirectory.listFiles(filter))
  }

  private def fsEntryBaseDirectory(uuid: UUID): File = new File(storeBaseDirectory, uuid.toString)
  private def fsEntryInfo(uuid: UUID): File = new File(fsEntryBaseDirectory(uuid), "about")
  
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

  private def makeWebSocketJsonFile(entryUUID: UUID, uuid: UUID) = new File(fsEntryBaseDirectory(entryUUID), s"$uuid.wsjson")

  def jsonRead[T](file: File)(implicit codec: JsonValueCodec[T]): T = {
    readFromString(FileUtils.readFileToString(file, "UTF-8"))
  }

  def jsonWrite[T](file: File, value: T)(implicit codec: JsonValueCodec[T]) = {
    val tmpFile = new File(file.getParent, file.getName + ".tmp")
    FileUtils.write(tmpFile, writeToString(value), "UTF-8")
    tmpFile.renameTo(file)
  }

  // -- Actor Logic --
  
  private def echoBehavior(id: UUID): Behavior[Command] = Behaviors.setup { context =>
    var storage: Option[HashedIndexedFileStorage] = None
    
    // Set inactivity timeout
    context.setReceiveTimeout(config.webEcho.behavior.storageHandleTtl.toMillis.millis, ReceiveTimeout)
    
    def getOrOpenStorage(): Try[HashedIndexedFileStorage] = {
      storage match {
        case Some(s) => Success(s)
        case None =>
          val dest = fsEntryBaseDirectory(id)
          val res = HashedIndexedFileStorageLive(dest.getAbsolutePath, shaGoal = shaGoal)
          res.foreach(s => storage = Some(s))
          res
      }
    }

    Behaviors
      .receiveMessage[Command] {
        case ReceiveTimeout =>
          // Inactivity timeout reached, stop the actor
          Behaviors.stopped

        case GetEchoInfo(_, replyTo) =>
          val info = getOrOpenStorage().toOption.map { s =>
            val echo = Try(jsonRead[Echo](fsEntryInfo(id))).toOption
            EchoInfo(
              count = s.count().toOption.getOrElse(0),
              updatedOn = s.updatedOn().toOption.flatten.map(Instant.ofEpochMilli),
              origin = echo.flatMap(_.origin)
            )
          }
          replyTo ! info
          Behaviors.same

        case AddEchoContent(_, content, replyTo) =>
          val result = getOrOpenStorage().flatMap { s =>
            s.append(writeToString(content)).map { result =>
              ReceiptProof(
                index = result.index,
                timestamp = result.timestamp,
                nonce = result.nonce,
                sha256 = result.sha.toString
              )
            }
          }
          replyTo ! result
          Behaviors.same

        case GetEchoContent(_, replyTo) =>
          val res = if (!fsEntryBaseDirectory(id).exists()) None else {
            getOrOpenStorage().toOption.flatMap { s =>
              s.list(reverseOrder = true).map(_.map(json => readFromString[Record](json))).toOption
            }
          }
          replyTo ! res
          Behaviors.same

        case GetEchoContentWithProof(_, replyTo) =>
          val res = if (!fsEntryBaseDirectory(id).exists()) None else {
            getOrOpenStorage().toOption.flatMap { s =>
              s.listWithMeta(reverseOrder = true).map(_.map { case (meta, content) =>
                val proof = ReceiptProof(meta.index, meta.timestamp, meta.nonce, meta.sha.toString)
                (proof, readFromString[Record](content))
              }).toOption
            }
          }
          replyTo ! res
          Behaviors.same
          
        case CreateEcho(_, origin, replyTo) =>
          val dest = fsEntryBaseDirectory(id)
          dest.mkdirs()
          HashedIndexedFileStorageLive(dest.getAbsolutePath, shaGoal = shaGoal) match {
            case Success(s) =>
              storage = Some(s)
              jsonWrite(fsEntryInfo(id), Echo(id = id, origin = origin))
            case Failure(e) =>
              logger.error(s"Failed to create storage for $id", e)
          }
          replyTo ! ()
          Behaviors.same

        case AddWebSocket(_, uri, userData, origin, expiresAt, replyTo) =>
          val uuid = UniqueIdentifiers.timedUUID()
          val ws = WebSocket(uuid, uri, userData, origin, expiresAt)
          jsonWrite(makeWebSocketJsonFile(id, uuid), ws)
          replyTo ! ws
          Behaviors.same

        case GetWebSocket(_, wsId, replyTo) =>
          val f = makeWebSocketJsonFile(id, wsId)
          replyTo ! (if (f.exists()) Try(jsonRead[WebSocket](f)).toOption else None)
          Behaviors.same

        case DeleteWebSocket(_, wsId, replyTo) =>
          val f = makeWebSocketJsonFile(id, wsId)
          replyTo ! (if (f.exists()) Some(f.delete()) else None)
          Behaviors.same

        case ListWebSockets(_, replyTo) =>
          val result = fsEntryWebSocketsFiles(id).map(_.flatMap(f => Try(jsonRead[WebSocket](f)).toOption))
          replyTo ! result.map(_.to(Iterable))
          Behaviors.same

        case _ => Behaviors.unhandled
      }
      .receiveSignal {
        case (_, PostStop) =>
          // logger.debug(s"Actor for $id stopped")
          Behaviors.same
      }
  }

  // Main Manager Behavior
  private def managerBehavior(): Behavior[Command] = Behaviors.setup { context =>
    var children = Map.empty[UUID, ActorRef[Command]]

    def getChild(id: UUID): ActorRef[Command] = {
      children.getOrElse(id, {
        val child = context.spawnAnonymous(echoBehavior(id))
        context.watch(child)
        children += id -> child
        child
      })
    }

    Behaviors
      .receiveMessage[Command] {
        case cmd @ GetEchoInfo(id, _) => getChild(id) ! cmd; Behaviors.same
        case cmd @ AddEchoContent(id, _, _) => getChild(id) ! cmd; Behaviors.same
        case cmd @ GetEchoContent(id, _) => getChild(id) ! cmd; Behaviors.same
        case cmd @ GetEchoContentWithProof(id, _) => getChild(id) ! cmd; Behaviors.same
        case cmd @ CreateEcho(id, _, _) => getChild(id) ! cmd; Behaviors.same
        
        case cmd @ AddWebSocket(id, _, _, _, _, _) => getChild(id) ! cmd; Behaviors.same
        case cmd @ GetWebSocket(id, _, _) => getChild(id) ! cmd; Behaviors.same
        case cmd @ DeleteWebSocket(id, _, _) => getChild(id) ! cmd; Behaviors.same
        case cmd @ ListWebSockets(id, _) => getChild(id) ! cmd; Behaviors.same
        
        case DeleteEcho(id, replyTo) =>
          children.get(id).foreach(context.stop)
          children -= id
          // Actual FS deletion
          val files = List(Array(fsEntryInfo(id))) ++ fsEntryFiles(id) ++ fsEntryWebSocketsFiles(id)
          files.flatten.foreach(_.delete())
          fsEntryBaseDirectory(id).delete()
          replyTo ! ()
          Behaviors.same

        case CheckEchoExists(id, replyTo) =>
          replyTo ! fsEntryBaseDirectory(id).exists()
          Behaviors.same

        case GetStoreInfo(replyTo) =>
          val count = fsEntries().map(_.length).getOrElse(0)
          replyTo ! Some(StoreInfo(count = count, lastUpdated = None))
          Behaviors.same

        case GetStoreList(replyTo) =>
          replyTo ! fsEntryUUIDs()
          Behaviors.same
          
        case _ => Behaviors.unhandled
      }
      .receiveSignal {
        case (ctx, Terminated(childRef)) =>
          children.collectFirst { case (id, ref) if ref == childRef => id }
            .foreach(children -= _)
          Behaviors.same
      }
  }

  private val system = ActorSystem(managerBehavior(), "EchoStoreSystem")
  implicit val timeout: Timeout = 5.seconds
  implicit val ec: ExecutionContext = system.executionContext
  implicit val scheduler: Scheduler = system.scheduler

  private def ask[T](f: ActorRef[T] => Command): T = Await.result(system.ask(f), timeout.duration)

  // -- Interface Implementation --

  override def storeInfo(): Option[StoreInfo] = ask(GetStoreInfo(_))

  override def storeList(): Iterable[UUID] = ask(GetStoreList(_))

  override def echoInfo(id: UUID): Option[EchoInfo] = ask(GetEchoInfo(id, _))

  override def echoExists(id: UUID): Boolean = ask(CheckEchoExists(id, _))

  override def echoDelete(id: UUID): Unit = ask(DeleteEcho(id, _))

  override def echoAdd(id: UUID, origin: Option[Origin]): Unit = ask(CreateEcho(id, origin, _))

  override def echoGet(id: UUID): Option[CloseableIterator[Record]] = ask(GetEchoContent(id, _))

  override def echoGetWithProof(id: UUID): Option[CloseableIterator[(ReceiptProof, Record)]] = ask(GetEchoContentWithProof(id, _))

  override def echoAddContent(id: UUID, content: Any): Try[ReceiptProof] = ask(AddEchoContent(id, content, _))

  override def webSocketAdd(echoId: UUID, uri: String, userData: Option[String], origin: Option[Origin], expiresAt: Option[OffsetDateTime]): WebSocket =
    ask(AddWebSocket(echoId, uri, userData, origin, expiresAt, _))

  override def webSocketGet(echoId: UUID, id: UUID): Option[WebSocket] = ask(GetWebSocket(echoId, id, _))

  override def webSocketDelete(echoId: UUID, id: UUID): Option[Boolean] = ask(DeleteWebSocket(echoId, id, _))

  override def webSocketList(echoId: UUID): Option[Iterable[WebSocket]] = ask(ListWebSockets(echoId, _))
}
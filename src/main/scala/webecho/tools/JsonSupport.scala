package webecho.tools

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import webecho.apimodel._
import webecho.model._

trait JsonSupport {
  // Force recompile
  implicit val apiRecorderCodec: JsonValueCodec[ApiRecorder] = JsonCodecMaker.make
  implicit val apiRecordCodec: JsonValueCodec[ApiRecord] = JsonCodecMaker.make
  
  implicit val apiNotFoundCodec: JsonValueCodec[ApiNotFound] = JsonCodecMaker.make
  implicit val apiBadRequestCodec: JsonValueCodec[ApiBadRequest] = JsonCodecMaker.make
  implicit val apiForbiddenCodec: JsonValueCodec[ApiForbidden] = JsonCodecMaker.make
  implicit val apiInternalErrorCodec: JsonValueCodec[ApiInternalError] = JsonCodecMaker.make
  implicit val apiPreconditionFailedCodec: JsonValueCodec[ApiPreconditionFailed] = JsonCodecMaker.make
  
  implicit val apiHealthCodec: JsonValueCodec[ApiHealth] = JsonCodecMaker.make
  implicit val apiApiReceiptProofCodec: JsonValueCodec[ApiReceiptProof] = JsonCodecMaker.make
  implicit val apiServiceInfoCodec: JsonValueCodec[ApiServiceInfo] = JsonCodecMaker.make
  implicit val apiWebSocketCodec: JsonValueCodec[ApiWebSocket] = JsonCodecMaker.make
  implicit val apiListWebSocketCodec: JsonValueCodec[List[ApiWebSocket]] = JsonCodecMaker.make
  implicit val apiWebSocketInputCodec: JsonValueCodec[ApiWebSocketSpec] = JsonCodecMaker.make
  implicit val apiOriginCodec: JsonValueCodec[ApiOrigin] = JsonCodecMaker.make

  implicit val receiptProofCodec: JsonValueCodec[ReceiptProof] = JsonCodecMaker.make
  implicit val storeInfoCodec: JsonValueCodec[StoreInfo] = JsonCodecMaker.make
  implicit val echoCodec: JsonValueCodec[Echo] = JsonCodecMaker.make
  implicit val echoInfoCodec: JsonValueCodec[EchoInfo] = JsonCodecMaker.make
  implicit val webSocketCodec: JsonValueCodec[WebSocket] = JsonCodecMaker.make
  implicit val originCodec: JsonValueCodec[Origin] = JsonCodecMaker.make

  val mapAnyCodec: JsonValueCodec[Map[String, Any]] = JsonCodecMaker.make
  val listAnyCodec: JsonValueCodec[List[Any]] = JsonCodecMaker.make

  implicit val anyCodec: JsonValueCodec[Any] = new JsonValueCodec[Any] {
    override def decodeValue(in: JsonReader, default: Any): Any = {
      val b = in.nextToken()
      in.rollbackToken()
      if (b == '{') {
        mapAnyCodec.decodeValue(in, Map.empty)
      } else if (b == '[') {
        listAnyCodec.decodeValue(in, Nil)
      } else if (b == '"') {
        in.readString(null)
      } else if (b == 't' || b == 'f') {
        in.readBoolean()
      } else if (b == 'n') {
        in.readNullOrError(default, "expected null")
      } else {
        in.readDouble() 
      }
    }

    override def encodeValue(x: Any, out: JsonWriter): Unit = {
      x match {
        case m: Map[_, _] => mapAnyCodec.encodeValue(m.asInstanceOf[Map[String, Any]], out)
        case l: Iterable[_] => listAnyCodec.encodeValue(l.toList, out)
        case s: String => out.writeVal(s)
        case i: Int => out.writeVal(i)
        case l: Long => out.writeVal(l)
        case d: Double => out.writeVal(d)
        case f: Float => out.writeVal(f)
        case b: Boolean => out.writeVal(b)
        case null => out.writeNull()
        case None => out.writeNull()
        case Some(v) => encodeValue(v, out)
        case other => out.writeVal(other.toString)
      }
    }

    override def nullValue: Any = null
  }
}

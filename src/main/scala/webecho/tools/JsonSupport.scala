package webecho.tools

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import webecho.apimodel.*
import webecho.model.*

import java.math.MathContext

trait JsonSupport {
  // Force recompile
  implicit val apiRecorderCodec: JsonValueCodec[ApiRecorder] = JsonCodecMaker.make
  implicit val apiRecordCodec: JsonValueCodec[ApiRecord]     = JsonCodecMaker.make

  implicit val apiNotFoundCodec: JsonValueCodec[ApiErrorNotFound]                     = JsonCodecMaker.make
  implicit val apiBadRequestCodec: JsonValueCodec[ApiErrorBadRequest]                 = JsonCodecMaker.make
  implicit val apiForbiddenCodec: JsonValueCodec[ApiErrorForbidden]                   = JsonCodecMaker.make
  implicit val apiInternalErrorCodec: JsonValueCodec[ApiErrorInternalIssue]           = JsonCodecMaker.make
  implicit val apiPreconditionFailedCodec: JsonValueCodec[ApiErrorPreconditionFailed] = JsonCodecMaker.make

  implicit val apiHealthCodec: JsonValueCodec[ApiHealth]                 = JsonCodecMaker.make
  implicit val apiApiReceiptProofCodec: JsonValueCodec[ApiReceiptProof]  = JsonCodecMaker.make
  implicit val apiServiceInfoCodec: JsonValueCodec[ApiServiceInfo]       = JsonCodecMaker.make
  implicit val apiWebSocketCodec: JsonValueCodec[ApiWebSocket]           = JsonCodecMaker.make
  implicit val apiListWebSocketCodec: JsonValueCodec[List[ApiWebSocket]] = JsonCodecMaker.make
  implicit val apiWebSocketInputCodec: JsonValueCodec[ApiWebSocketSpec]  = JsonCodecMaker.make
  implicit val apiOriginCodec: JsonValueCodec[ApiOrigin]                 = JsonCodecMaker.make
  implicit val apiWebhookCodec: JsonValueCodec[ApiWebhook]               = JsonCodecMaker.make

  implicit val receiptProofCodec: JsonValueCodec[ReceiptProof] = JsonCodecMaker.make
  implicit val storeInfoCodec: JsonValueCodec[StoreInfo]       = JsonCodecMaker.make
  implicit val echoCodec: JsonValueCodec[Echo]                 = JsonCodecMaker.make
  implicit val echoInfoCodec: JsonValueCodec[EchoInfo]         = JsonCodecMaker.make
  implicit val webSocketCodec: JsonValueCodec[WebSocket]       = JsonCodecMaker.make
  implicit val webSocketListCodec: JsonValueCodec[List[WebSocket]] = JsonCodecMaker.make
  implicit val originCodec: JsonValueCodec[Origin]             = JsonCodecMaker.make
  implicit val webhookCodec: JsonValueCodec[Webhook]           = JsonCodecMaker.make
  implicit val recordCodec: JsonValueCodec[Record]             = JsonCodecMaker.make

  val mapAnyCodec: JsonValueCodec[Map[String, Any]] = JsonCodecMaker.make
  val listAnyCodec: JsonValueCodec[List[Any]]       = JsonCodecMaker.make

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
        val num = in.readBigDecimal(
          0,
          MathContext.UNLIMITED,
          JsonReader.bigDecimalScaleLimit,
          JsonReader.bigDecimalDigitsLimit
        )
        num match {
          case n if n.isValidInt      => n.toIntExact
          case n if n.isValidLong     => n.toLongExact
          case n if n.isDecimalFloat  => n.toFloat
          case n if n.isDecimalDouble => n.toDouble
          case n if n.isWhole         => n.toBigInt
          case n                      => n
        }
      }
    }

    override def encodeValue(x: Any, out: JsonWriter): Unit = {
      x match {
        case m: Map[_, _]   => mapAnyCodec.encodeValue(m.asInstanceOf[Map[String, Any]], out)
        case l: Iterable[_] => listAnyCodec.encodeValue(l.toList, out)
        case s: String      => out.writeVal(s)
        case i: Int         => out.writeVal(i)
        case l: Long        => out.writeVal(l)
        case d: Double      => out.writeVal(d)
        case f: Float       => out.writeVal(f)
        case b: Boolean     => out.writeVal(b)
        case v: BigInt      => out.writeVal(v)
        case v: BigDecimal  => out.writeVal(v)
        case w: Webhook     => webhookCodec.encodeValue(w, out)
        case w: WebSocket   => webSocketCodec.encodeValue(w, out)
        case null           => out.writeNull()
        case None           => out.writeNull()
        case Some(v)        => encodeValue(v, out)
        case other          => out.writeVal(other.toString)
      }
    }

    override def nullValue: Any = null
  }
}

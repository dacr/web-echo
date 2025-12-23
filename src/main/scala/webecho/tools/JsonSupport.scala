package webecho.tools

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import webecho.apimodel.*
import webecho.model.*

import java.math.MathContext
import scala.concurrent.duration.Duration

object JsonSupport {
  // Force recompile 2
  given durationCodec: JsonValueCodec[Duration] = new JsonValueCodec[Duration] {
    override def decodeValue(in: JsonReader, default: Duration): Duration = {
      Duration.fromNanos(in.readLong())
    }
    override def encodeValue(x: Duration, out: JsonWriter): Unit = {
      out.writeVal(x.toNanos)
    }
    override def nullValue: Duration = null
  }

  given apiRecorderCodec: JsonValueCodec[ApiRecorder]             = JsonCodecMaker.make
  given apiRecorderUpdateCodec: JsonValueCodec[ApiRecorderUpdate] = JsonCodecMaker.make
  given apiRecordCodec: JsonValueCodec[ApiRecord]                 = JsonCodecMaker.make

  given apiNotFoundCodec: JsonValueCodec[ApiErrorNotFound]                     = JsonCodecMaker.make
  given apiBadRequestCodec: JsonValueCodec[ApiErrorBadRequest]                 = JsonCodecMaker.make
  given apiForbiddenCodec: JsonValueCodec[ApiErrorForbidden]                   = JsonCodecMaker.make
  given apiInternalErrorCodec: JsonValueCodec[ApiErrorInternalIssue]           = JsonCodecMaker.make
  given apiPreconditionFailedCodec: JsonValueCodec[ApiErrorPreconditionFailed] = JsonCodecMaker.make

  given apiHealthCodec: JsonValueCodec[ApiHealth]                 = JsonCodecMaker.make
  given apiApiReceiptProofCodec: JsonValueCodec[ApiReceiptProof]  = JsonCodecMaker.make
  given apiServiceInfoCodec: JsonValueCodec[ApiServiceInfo]       = JsonCodecMaker.make
  given apiWebSocketCodec: JsonValueCodec[ApiWebSocket]           = JsonCodecMaker.make
  given apiListWebSocketCodec: JsonValueCodec[List[ApiWebSocket]] = JsonCodecMaker.make
  given apiWebSocketInputCodec: JsonValueCodec[ApiWebSocketSpec]  = JsonCodecMaker.make
  given apiOriginCodec: JsonValueCodec[ApiOrigin]                 = JsonCodecMaker.make
  given apiWebhookCodec: JsonValueCodec[ApiWebhook]               = JsonCodecMaker.make

  given receiptProofCodec: JsonValueCodec[ReceiptProof]     = JsonCodecMaker.make
  given storeInfoCodec: JsonValueCodec[StoreInfo]           = JsonCodecMaker.make
  given echoCodec: JsonValueCodec[Echo]                     = JsonCodecMaker.make
  given echoInfoCodec: JsonValueCodec[EchoInfo]             = JsonCodecMaker.make
  given webSocketCodec: JsonValueCodec[WebSocket]           = JsonCodecMaker.make
  given webSocketListCodec: JsonValueCodec[List[WebSocket]] = JsonCodecMaker.make
  given originCodec: JsonValueCodec[Origin]                 = JsonCodecMaker.make
  given webhookCodec: JsonValueCodec[Webhook]               = JsonCodecMaker.make
  given recordCodec: JsonValueCodec[Record]                 = JsonCodecMaker.make

  given mapAnyCodec: JsonValueCodec[Map[String, Any]] = JsonCodecMaker.make
  given listAnyCodec: JsonValueCodec[List[Any]]       = JsonCodecMaker.make

  given anyCodec: JsonValueCodec[Any] = new JsonValueCodec[Any] {
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

package webecho

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.model.{HttpEntity, MediaTypes, RequestEntity}
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.util.ByteString
import webecho.apimodel.{ApiRecorderUpdate, ApiWebSocketSpec}
import webecho.tools.JsonSupport.given

object JsoniterScalaTestSupport {
  given unmarshaller[A](using codec: JsonValueCodec[A]): FromEntityUnmarshaller[A] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map {
        case ByteString.empty => throw Unmarshaller.NoContentException
        case data             => readFromArray(data.toArray)
      }

  given marshaller[A](using codec: JsonValueCodec[A]): Marshaller[A, RequestEntity] =
    Marshaller.withFixedContentType(MediaTypes.`application/json`) { a =>
      HttpEntity(MediaTypes.`application/json`, writeToArray(a))
    }

  // Resolve ambiguity for ApiWebSocketSpec marshaller
  given apiWebSocketSpecMarshaller: Marshaller[ApiWebSocketSpec, RequestEntity]   = marshaller(using apiWebSocketInputCodec)
  given apiRecorderUpdateMarshaller: Marshaller[ApiRecorderUpdate, RequestEntity] = marshaller(using apiRecorderUpdateCodec)
  given mapStringStringMarshaller: Marshaller[Map[String, String], RequestEntity] = marshaller(using JsonCodecMaker.make[Map[String, String]])

}

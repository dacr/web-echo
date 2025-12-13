package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiWebSocketSpec(
  uri: String,
  userData: Option[String]
)

object ApiWebSocketSpec {
  implicit val schema: Schema[ApiWebSocketSpec] =
    Schema
      .derived[ApiWebSocketSpec]
      .name(SName("WebSocketSpec"))
      .description("Specification of a websocket to connect to and from which received data will be stored in the recorder")
      .modify(_.uri)(_.description("Websocket URL to connect to"))
      .modify(_.userData)(_.description("Optional user data which will be kept in the recorded meta data"))
}

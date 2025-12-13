package webecho.apimodel

import java.util.UUID
import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiWebSocket(
  id: UUID,
  uri: String,
  userData: Option[String]
)
object ApiWebSocket {
  implicit val schema: Schema[ApiWebSocket] =
    Schema
      .derived[ApiWebSocket]
      .name(SName("WebSocket"))
      .description("WebSocket to connect to and from which received data will be stored in the recorder")
      .modify(_.id)(_.description("Websocket identifier"))
      .modify(_.uri)(_.description("Websocket URL from which we will receive data"))
      .modify(_.userData)(_.description("Optional user data which is kept in the recorded meta data"))
}

package webecho.apimodel

import java.util.UUID
import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiWebSocket(
  uri: String,
  userInfo: Option[String],
  uuid: UUID
)
object ApiWebSocket {
  implicit val schema: Schema[ApiWebSocket] = Schema.derived[ApiWebSocket].name(SName("WebSocket"))
}

package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiWebSocketInput(
  uri: String,
  userData: Option[String]
)
object ApiWebSocketInput {
  implicit val schema: Schema[ApiWebSocketInput] = Schema.derived[ApiWebSocketInput].name(SName("WebSocketInput"))
}

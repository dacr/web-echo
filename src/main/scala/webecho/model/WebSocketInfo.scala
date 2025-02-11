package webecho.model

import java.util.UUID

case class WebSocketInfo(
  uuid: UUID,
  uri: String,
  userData: Option[String]
)

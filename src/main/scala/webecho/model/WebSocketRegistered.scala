package webecho.model

import java.util.UUID

case class WebSocketRegistered(
  uuid: UUID,
  uri: String,
  userData: Option[String]
)

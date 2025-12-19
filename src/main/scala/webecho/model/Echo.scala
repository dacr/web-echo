package webecho.model

import java.util.UUID

case class Echo(
  id: UUID,
  description: Option[String],
  origin: Option[Origin]
)

package webecho.model

import java.util.UUID

case class Echo(
  id: UUID,
  origin: Option[Origin]
)

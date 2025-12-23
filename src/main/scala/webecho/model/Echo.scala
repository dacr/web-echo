package webecho.model

import java.util.UUID
import scala.concurrent.duration.Duration

case class Echo(
  id: UUID,
  description: Option[String],
  lifeExpectancy: Option[Duration],
  origin: Option[Origin]
)

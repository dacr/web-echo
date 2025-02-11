package webecho.model

import java.time.OffsetDateTime
import java.util.UUID

case class AppInfo(
  entriesCount: Long,
  instanceUUID: UUID,
  startedOn: OffsetDateTime,
  version: String,
  buildDate: Option[String]
)

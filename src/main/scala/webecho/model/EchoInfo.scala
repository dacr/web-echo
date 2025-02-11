package webecho.model

import java.time.OffsetDateTime

case class EchoInfo(
  echoCount: Long,
  lastUpdated: Option[OffsetDateTime],
  createdByRemoteHostAddress: Option[String],
  createdByUserAgent: Option[String],
  createdOn: Option[OffsetDateTime]
)

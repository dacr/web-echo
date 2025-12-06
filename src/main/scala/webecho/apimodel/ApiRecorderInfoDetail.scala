package webecho.apimodel

import java.time.OffsetDateTime
import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiRecorderInfoDetail(
  echoCount: Long,
  lastUpdated: Option[OffsetDateTime],
  createdByRemoteHostAddress: Option[String],
  createdByUserAgent: Option[String],
  createdOn: Option[OffsetDateTime]
)
object ApiRecorderInfoDetail {
  implicit val schema: Schema[ApiRecorderInfoDetail] = Schema.derived[ApiRecorderInfoDetail].name(SName("RecorderInfoDetail"))
}

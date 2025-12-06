package webecho.apimodel

import java.time.OffsetDateTime
import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiServiceInfo(
  entriesCount: Long,
  instanceUUID: String,
  startedOn: OffsetDateTime,
  version: String,
  buildDate: Option[String]
)
object ApiServiceInfo {
  implicit val schema: Schema[ApiServiceInfo] = Schema.derived[ApiServiceInfo].name(SName("ServiceInfo"))
}

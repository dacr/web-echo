package webecho.apimodel

import java.time.OffsetDateTime
import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiServiceInfo(
  recordersCount: Long,
  startedOn: OffsetDateTime,
  version: String,
  buildDate: Option[String]
)
object ApiServiceInfo {
  implicit val schema: Schema[ApiServiceInfo] =
    Schema
      .derived[ApiServiceInfo]
      .name(SName("ServiceInfo"))
      .description("Information about the web-echo recording service")
      .modify(_.recordersCount)(_.description("Number of recorders currently active"))
      .modify(_.startedOn)(_.description("When the service has been started"))
      .modify(_.version)(_.description("Version of the web-echo service"))
      .modify(_.buildDate)(_.description("Date of the build of the web-echo service"))
}

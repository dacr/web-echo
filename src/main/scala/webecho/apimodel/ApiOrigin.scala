package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

import java.time.OffsetDateTime

case class ApiOrigin(
  createdOn: OffsetDateTime,
  createdByIpAddress: Option[String],
  createdByUserAgent: Option[String]
)
object ApiOrigin {
  implicit val schema: Schema[ApiOrigin] =
    Schema
      .derived[ApiOrigin]
      .name(SName("Origin"))
      .modify(_.createdOn)(_.description("When it has been created/added"))
      .modify(_.createdByIpAddress)(_.description("from which client IP address this operation was done"))
      .modify(_.createdByUserAgent)(_.description("from which declared client User-Agent this operation was done, if any"))
}

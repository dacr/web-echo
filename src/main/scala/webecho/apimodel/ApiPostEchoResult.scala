package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiPostEchoResult(
  message: String,
  meta: Option[ApiPostEchoMeta] = None
)
object ApiPostEchoResult {
  implicit val schema: Schema[ApiPostEchoResult] = Schema.derived[ApiPostEchoResult].name(SName("PostEchoResult"))
}

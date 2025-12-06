package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiPostEchoMeta(
  sha256: String,
  index: Long,
  timestamp: Long
)
object ApiPostEchoMeta {
  implicit val schema: Schema[ApiPostEchoMeta] = Schema.derived[ApiPostEchoMeta].name(SName("PostEchoMeta"))
}

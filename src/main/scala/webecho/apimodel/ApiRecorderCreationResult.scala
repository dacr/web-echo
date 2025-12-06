package webecho.apimodel

import java.util.UUID
import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiRecorderCreationResult(
  uuid: UUID,
  url: String
)
object ApiRecorderCreationResult {
  implicit val schema: Schema[ApiRecorderCreationResult] = Schema.derived[ApiRecorderCreationResult].name(SName("RecorderCreationResult"))
}

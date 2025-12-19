package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiRecorderUpdate(
  description: Option[String]
)

object ApiRecorderUpdate {
  implicit val schema: Schema[ApiRecorderUpdate] =
    Schema
      .derived[ApiRecorderUpdate]
      .name(SName("RecorderUpdate"))
      .description("Updates for a recorder configuration")
      .modify(_.description)(_.description("New description for the recorder"))
}

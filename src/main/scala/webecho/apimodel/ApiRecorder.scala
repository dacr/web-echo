package webecho.apimodel

import java.util.UUID
import sttp.tapir.Schema
import sttp.tapir.Schema.SName

import java.time.OffsetDateTime

case class ApiRecorder(
  id: UUID,
  dataTargetURL: String,
  origin: Option[ApiOrigin],
  updatedOn: Option[OffsetDateTime],
  recordsCount: Option[Long]
)

object ApiRecorder {
  implicit val schema: Schema[ApiRecorder] =
    Schema
      .derived[ApiRecorder]
      .name(SName("Recorder"))
      .description("All the information about a recorder, including the URL where data can be sent to if the webhook mode is use")
      .modify(_.id)(_.description("Unique identifier for the recorder"))
      .modify(_.dataTargetURL)(_.description("The generated URL where JSON data can be send for this recorder, usable as a webhook"))
      .modify(_.origin)(_.description("Some contextual data about the origin of this recorder"))
      .modify(_.updatedOn)(_.description("When the last record was added to this recorder"))
      .modify(_.recordsCount)(_.description("The number of records currently stored in this recorder"))
}

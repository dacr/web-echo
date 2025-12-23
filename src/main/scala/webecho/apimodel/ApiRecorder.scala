package webecho.apimodel

import java.util.UUID
import sttp.tapir.Schema
import sttp.tapir.Schema.SName

import java.time.OffsetDateTime
import scala.concurrent.duration.Duration

case class ApiRecorder(
  id: UUID,
  description: Option[String],
  lifeExpectancy: Option[Duration],
  sendDataURL: String,
  fetchDataURL: String,
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
      .modify(_.description)(_.description("Description for the recorder"))
      .modify(_.lifeExpectancy)(_.description("New life expectancy for the recorder, not set mean no limit. (10 hours, 42 days, 15 minutes)"))
      .modify(_.sendDataURL)(_.description("The generated URL where JSON data can be send (POST/GET/PUT) for this recorder, usable as a webhook"))
      .modify(_.fetchDataURL)(_.description("The generated URL where JSON data can be fetched for this recorder"))
      .modify(_.origin)(_.description("Some contextual data about the origin of this recorder"))
      .modify(_.updatedOn)(_.description("When the last record was added to this recorder"))
      .modify(_.recordsCount)(_.description("The number of records currently stored in this recorder"))
}

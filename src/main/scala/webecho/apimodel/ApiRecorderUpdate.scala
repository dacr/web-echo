package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

import scala.concurrent.duration.Duration

case class ApiRecorderUpdate(
  description: Option[String],
  lifeExpectancy: Option[Duration]
)

object ApiRecorderUpdate {
  implicit val schema: Schema[ApiRecorderUpdate] =
    Schema
      .derived[ApiRecorderUpdate]
      .name(SName("RecorderUpdate"))
      .description("Updates for a recorder configuration")
      .modify(_.description)(_.description("New description for the recorder"))
      .modify(_.lifeExpectancy)(_.description("New life expectancy for the recorder, not set mean no limit. (10 hours, 42 days, 15 minutes)"))
}

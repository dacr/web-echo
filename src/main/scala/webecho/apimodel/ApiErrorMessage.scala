package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiErrorMessage(
  message: String
)
object ApiErrorMessage {
  implicit val schema: Schema[ApiErrorMessage] =
    Schema
      .derived[ApiErrorMessage]
      .name(SName("ErrorMessage"))
      .description("Something went wrong")
      .modify(_.message)(_.description("Descriptive message for the error"))
}

package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiHealth(
  alive: Boolean = true,
  description: String = "alive"
)
object ApiHealth {
  implicit val schema: Schema[ApiHealth] =
    Schema
      .derived[ApiHealth]
      .name(SName("Health"))
      .description("Health of the service")
      .modify(_.alive)(_.description("Is the service alive?"))
      .modify(_.description)(_.description("Some textual description of the state of this service"))
}

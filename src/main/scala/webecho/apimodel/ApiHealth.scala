package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiHealth(
  alive: Boolean = true,
  description: String = "alive"
)
object ApiHealth {
  implicit val schema: Schema[ApiHealth] = Schema.derived[ApiHealth].name(SName("Health"))
}

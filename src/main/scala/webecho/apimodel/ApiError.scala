package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

sealed trait ApiError {
  def message: String
}

case class ApiNotFound(message: String) extends ApiError
object ApiNotFound {
  implicit val schema: Schema[ApiNotFound] = Schema.derived[ApiNotFound].name(SName("NotFound"))
}

case class ApiBadRequest(message: String) extends ApiError
object ApiBadRequest {
  implicit val schema: Schema[ApiBadRequest] = Schema.derived[ApiBadRequest].name(SName("BadRequest"))
}

case class ApiForbidden(message: String) extends ApiError
object ApiForbidden {
  implicit val schema: Schema[ApiForbidden] = Schema.derived[ApiForbidden].name(SName("Forbidden"))
}

case class ApiInternalError(message: String) extends ApiError
object ApiInternalError {
  implicit val schema: Schema[ApiInternalError] = Schema.derived[ApiInternalError].name(SName("InternalError"))
}

case class ApiPreconditionFailed(message: String) extends ApiError
object ApiPreconditionFailed {
  implicit val schema: Schema[ApiPreconditionFailed] = Schema.derived[ApiPreconditionFailed].name(SName("PreconditionFailed"))
}

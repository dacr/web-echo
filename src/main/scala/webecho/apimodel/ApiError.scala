package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

sealed trait ApiError {
  def message: String
}

case class ApiErrorNotFound(message: String) extends ApiError
object ApiErrorNotFound {
  implicit val schema: Schema[ApiErrorNotFound] = Schema.derived[ApiErrorNotFound].name(SName("ErrorNotFound"))
}

case class ApiErrorBadRequest(message: String) extends ApiError
object ApiErrorBadRequest {
  implicit val schema: Schema[ApiErrorBadRequest] = Schema.derived[ApiErrorBadRequest].name(SName("ErrorBadRequest"))
}

case class ApiErrorForbidden(message: String) extends ApiError
object ApiErrorForbidden {
  implicit val schema: Schema[ApiErrorForbidden] = Schema.derived[ApiErrorForbidden].name(SName("ErrorForbidden"))
}

case class ApiErrorInternalIssue(message: String) extends ApiError
object ApiErrorInternalIssue {
  implicit val schema: Schema[ApiErrorInternalIssue] = Schema.derived[ApiErrorInternalIssue].name(SName("ErrorInternalIssue"))
}

case class ApiErrorPreconditionFailed(message: String) extends ApiError
object ApiErrorPreconditionFailed {
  implicit val schema: Schema[ApiErrorPreconditionFailed] = Schema.derived[ApiErrorPreconditionFailed].name(SName("ErrorPreconditionFailed"))
}

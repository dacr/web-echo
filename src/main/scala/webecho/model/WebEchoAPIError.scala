package webecho.model

sealed trait WebEchoAPIError

case class WebEchoNotFound(message: String) extends WebEchoAPIError
case class InvalidRequest(message: String) extends WebEchoAPIError
case class ApplicationInternalError(message: String) extends WebEchoAPIError
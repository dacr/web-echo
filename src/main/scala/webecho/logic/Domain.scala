package webecho.logic

import webecho.security.UserProfile
import java.util.UUID

case class CommandContext(
  user: Option[UserProfile],
  ip: Option[String],
  userAgent: Option[String]
)

sealed trait LogicError
case class RecorderNotFound(id: UUID)   extends LogicError
case class AccessDenied(reason: String) extends LogicError
case object AccountPending              extends LogicError
case class InvalidInput(reason: String) extends LogicError
case class SystemError(reason: String)  extends LogicError

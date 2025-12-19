package webecho.model

case class Webhook(
  remoteHostAddress: Option[String],
  userAgent: Option[String]
)

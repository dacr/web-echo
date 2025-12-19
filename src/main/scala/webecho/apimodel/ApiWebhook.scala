package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiWebhook(
  remoteHostAddress: Option[String],
  userAgent: Option[String]
)

object ApiWebhook {
  implicit val schema: Schema[ApiWebhook] =
    Schema
      .derived[ApiWebhook]
      .name(SName("Webhook"))
      .description("Webhook information from which received data will be stored in the recorder")
      .modify(_.remoteHostAddress)(_.description("Remote IP address of the webhook sender"))
      .modify(_.userAgent)(_.description("User agent of the webhook sender"))
}

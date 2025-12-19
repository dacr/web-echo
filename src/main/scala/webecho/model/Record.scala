package webecho.model

case class Record(
  data: Any,
  addedOn: String,
  webhook: Option[Webhook] = None,
  websocket: Option[WebSocket] = None
)

package webecho.model

case class EchoWebSocket(
  uuid: String,
  uri: String,
  userData: Option[String],
  origin: Option[OperationOrigin]
)

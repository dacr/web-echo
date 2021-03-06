package webecho.model

case class EchoInfo(
  origin: Option[OperationOrigin],
  lastUpdated: Long,
  count: Long
)

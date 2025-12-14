package webecho.model

case class Record(
  data: Any,
  addedOn: String,
  addedByRemoteHostAddress: Option[String],
  addedByUserAgent: Option[String]
)

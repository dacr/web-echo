package webecho.model

case class OperationOrigin(
  createdOn: Long,
  createdByIpAddress: Option[String],
  createdByUserAgent: Option[String],
)

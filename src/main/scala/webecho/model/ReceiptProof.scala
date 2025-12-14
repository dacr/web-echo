package webecho.model

case class ReceiptProof(
  index: Long,
  timestamp: Long,
  nonce: Int,
  sha256: String
)

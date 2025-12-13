package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiReceiptProof(
  sha256: String,
  index: Long,
  timestamp: Long
)
object ApiReceiptProof {
  implicit val schema: Schema[ApiReceiptProof] =
    Schema
      .derived[ApiReceiptProof]
      .name(SName("ReceiptProof"))
      .modify(_.sha256)(_.description("Block chain data integrity proof, SHA256 hash of all the data sent up to this point"))
      .modify(_.index)(_.description("Position in the recorder block chain, which corresponds to the index of the last record sent"))
      .modify(_.timestamp)(_.description("Raw timestamp used by the block chain"))
}

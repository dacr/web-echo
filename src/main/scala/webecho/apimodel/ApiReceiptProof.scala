package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName

case class ApiReceiptProof(
  sha256: String,
  index: Long,
  timestamp: Long,
  nonce: Int
)
object ApiReceiptProof {
  implicit val schema: Schema[ApiReceiptProof] =
    Schema
      .derived[ApiReceiptProof]
      .name(SName("ReceiptProof"))
      .description("Proof of receipt that the sent data has been stored in this recorder block chain")
      .modify(_.sha256)(_.description("Block chain data integrity proof, SHA256 hash of all the data sent up to this point"))
      .modify(_.index)(_.description("Position in the recorder block chain, which corresponds to the index of the last record sent"))
      .modify(_.timestamp)(_.description("Raw timestamp used by the block chain"))
      .modify(_.nonce)(_.description("Nonce used for hash calculation"))
}

package webecho.tools

case class HashedIndexedMetaInternal(
  offset: Long, // Not stored, deducted from seek offset in meta file
  timestamp: Long,
  nonce: Int,
  dataOffset: Long,
  dataLength: Int,
  dataSHA: SHA
)

object HashedIndexedMetaInternal {
  // timestamp (Long) + nonce(Int) + dataIndex (Long) + dataLength (Int) + chosen SHA size
  def size(shaEngine: SHAEngine): Int = 8 + 4 + 8 + 4 + shaEngine.size
}

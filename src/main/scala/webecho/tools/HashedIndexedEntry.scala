package webecho.tools

type Timestamp = Long

case class HashedIndexEntry(
  index: Long, // Not stored, deducted from seek offset in index file
  timestamp: Timestamp,
  nonce: Int,
  dataIndex: Long,
  dataLength: Int,
  dataSHA: SHA
)

object HashedIndexEntry {
  // timestamp (Long) + nonce(Int) + dataIndex (Long) + dataLength (Int) + chosen SHA size
  def size(shaEngine: SHAEngine): Int = 8 + 4 + 8 + 4 + shaEngine.size
}

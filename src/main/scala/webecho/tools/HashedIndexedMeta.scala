package webecho.tools

case class HashedIndexedMeta(
  index: Long,
  timestamp: Long,
  nonce: Int,
  sha: SHA
)

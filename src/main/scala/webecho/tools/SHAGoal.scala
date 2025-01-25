package webecho.tools

trait SHAGoal {
  val goal: Array[Byte]
  def check(sha: SHA): Boolean = sha.bytes.startsWith(goal)
}

object SHAGoal {
  def standard(len: Int=4): SHAGoal = new SHAGoal {
    override val goal: Array[Byte] = Array.fill(len)(0)
  }

  def responseToEverything: SHAGoal = new SHAGoal {
    override val goal: Array[Byte] = Array(4, 2)
  }
}

package webecho.tools

trait SHAGoal {
  val goal:Array[Byte]
  def check(sha:SHA):Boolean = sha.bytes.startsWith(goal)
}

object SHAGoalStandard extends SHAGoal {
  val goal = Array.fill(4)(0)
}

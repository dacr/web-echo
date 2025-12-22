package webecho.security

case class UserProfile(roles: Set[String]) {
  def isPending: Boolean = roles.contains("pending")
}

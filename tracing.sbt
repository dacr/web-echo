
Compile / resourceGenerators +=  Def.task {
  val dir = (sourceManaged in Compile).value
  val projectName = name.value
  val projectGroup = organization.value
  val projectVersion = version.value
  val buildDateTime = java.time.Instant.now().toString
  val buildUUID = java.util.UUID.randomUUID.toString
  val file = dir / "webecho-meta.conf"
  IO.write(file,
    s"""web-echo {
       |  meta-info {
       |    project-name = "$projectName"
       |    project-group = "$projectGroup"
       |    build-version = "$projectVersion"
       |    build-date-time = "$buildDateTime"
       |    build-uuid = "$buildUUID"
       |  }
       |}""".stripMargin
  )
  Seq(file)
}.taskValue

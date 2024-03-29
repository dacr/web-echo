pomIncludeRepository := { _ => false }

releaseCrossBuild             := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value
publishMavenStyle             := true
Test / publishArtifact        := false

publishTo := (if (isSnapshot.value) Opts.resolver.sonatypeOssSnapshots.headOption else Some(Opts.resolver.sonatypeStaging))

PgpKeys.useGpg in Global := true                // workaround with pgp and sbt 1.2.x
pgpSecretRing            := pgpPublicRing.value // workaround with pgp and sbt 1.2.x

pomExtra in Global := {
  <developers>
    <developer>
      <id>dacr</id>
      <name>David Crosson</name>
      <url>https://github.com/dacr</url>
    </developer>
  </developers>
}

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  // runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)

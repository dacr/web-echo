name         := "web-echo"
organization := "fr.janalyse"
description  := "JSON data recorder"
maintainer   := "crosson.david@gmail.com"

licenses += "NON-AI-APACHE2" -> url(s"https://github.com/non-ai-licenses/non-ai-licenses/blob/main/NON-AI-APACHE2")

scalaVersion := "3.7.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

lazy val versions = new {
  // client side dependencies
  val swaggerui = "5.30.3"
  val bootstrap = "5.3.8"
  val jquery    = "3.7.1"
  val awesome   = "7.1.0"

  // server side dependencies
  val pureConfig     = "0.17.9"
  val pekko          = "1.4.0"
  val pekkoHttp      = "1.3.0"
  val jsoniterScala  = "2.38.6"
  val logback        = "1.5.22"
  val slf4j          = "2.0.17"
  val scalatest      = "3.2.19"
  val commonsio      = "2.21.0"
  val webjarsLocator = "0.52"
  val javaUUID       = "5.2.0"
  val tapir          = "1.13.3"
  val chimney        = "1.8.2"
  val caffeine       = "3.2.3"
}

// client side dependencies
libraryDependencies ++= Seq(
  "org.webjars" % "swagger-ui"   % versions.swaggerui,
  "org.webjars" % "bootstrap"    % versions.bootstrap,
  "org.webjars" % "jquery"       % versions.jquery,
  "org.webjars" % "font-awesome" % versions.awesome
)

// server side dependencies
libraryDependencies ++= Seq(
  "com.github.ben-manes.caffeine"          % "caffeine"                % versions.caffeine,
  "io.scalaland"                          %% "chimney"                 % versions.chimney,
  "com.softwaremill.sttp.tapir"           %% "tapir-core"              % versions.tapir,
  "com.softwaremill.sttp.tapir"           %% "tapir-pekko-http-server" % versions.tapir,
  "com.softwaremill.sttp.tapir"           %% "tapir-jsoniter-scala"    % versions.tapir,
  "com.softwaremill.sttp.tapir"           %% "tapir-swagger-ui-bundle" % versions.tapir,
  "com.github.pureconfig"                 %% "pureconfig-core"         % versions.pureConfig,
  "com.fasterxml.uuid"                     % "java-uuid-generator"     % versions.javaUUID,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"     % versions.jsoniterScala,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros"   % versions.jsoniterScala % "provided",
  "org.apache.pekko"                      %% "pekko-actor-typed"       % versions.pekko,
  "org.apache.pekko"                      %% "pekko-http"              % versions.pekkoHttp,
  "org.apache.pekko"                      %% "pekko-http-caching"      % versions.pekkoHttp,
  "org.apache.pekko"                      %% "pekko-stream"            % versions.pekko,
  "org.apache.pekko"                      %% "pekko-stream-typed"      % versions.pekko,
  "org.apache.pekko"                      %% "pekko-slf4j"             % versions.pekko,
  "org.apache.pekko"                      %% "pekko-testkit"           % versions.pekko         % Test,
  "org.apache.pekko"                      %% "pekko-stream-testkit"    % versions.pekko         % Test,
  "org.apache.pekko"                      %% "pekko-http-testkit"      % versions.pekkoHttp     % Test,
  "org.slf4j"                              % "slf4j-api"               % versions.slf4j,
  "ch.qos.logback"                         % "logback-classic"         % versions.logback,
  "commons-io"                             % "commons-io"              % versions.commonsio,
  "org.scalatest"                         %% "scalatest"               % versions.scalatest     % Test,
  "org.webjars"                            % "webjars-locator"         % versions.webjarsLocator
)

Compile / mainClass    := Some("webecho.Main")
packageBin / mainClass := Some("webecho.Main")

Test / testOptions += {
  val rel = scalaVersion.value.split("[.]").take(2).mkString(".")
  Tests.Argument(
    "-oDF", // -oW to remove colors
    "-u",
    s"target/junitresults/scala-$rel/"
  )
}

enablePlugins(JavaServerAppPackaging)
enablePlugins(SbtTwirl)

homepage   := Some(url("https://github.com/dacr/web-echo"))
scmInfo    := Some(ScmInfo(url(s"https://github.com/dacr/web-echo.git"), s"git@github.com:dacr/web-echo.git"))
developers := List(
  Developer(
    id = "dacr",
    name = "David Crosson",
    email = "crosson.david@gmail.com",
    url = url("https://github.com/dacr")
  )
)

Universal / topLevelDirectory := None
Universal / packageName       := s"${name.value}"

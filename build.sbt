name         := "web-echo"
organization := "fr.janalyse"
description  := "JSON data recorder"
maintainer   := "crosson.david@gmail.com"

licenses += "NON-AI-APACHE2" -> url(s"https://github.com/non-ai-licenses/non-ai-licenses/blob/main/NON-AI-APACHE2")

scalaVersion := "3.6.2"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

lazy val versions = new {
  // client side dependencies
  val swaggerui = "5.18.2"
  val bootstrap = "5.3.3"
  val jquery    = "3.7.1"
  val awesome   = "6.7.1"

  // server side dependencies
  val pureConfig      = "0.17.8"
  val pekko           = "1.1.2"
  val pekkoHttp       = "1.1.0"
  val pekkoHttpJson4s = "3.0.0"
  val json4s          = "4.0.7"
  val logback         = "1.5.16"
  val slf4j           = "2.0.16"
  val scalatest       = "3.2.19"
  val commonsio       = "2.18.0"
  val webjarsLocator  = "0.52"
  val javaUUID        = "5.1.0"
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
  "com.github.pureconfig" %% "pureconfig-core"      % versions.pureConfig,
  "com.fasterxml.uuid"     % "java-uuid-generator"  % versions.javaUUID,
  "org.json4s"            %% "json4s-jackson"       % versions.json4s,
  "org.json4s"            %% "json4s-ext"           % versions.json4s,
  "org.apache.pekko"      %% "pekko-actor-typed"    % versions.pekko,
  "org.apache.pekko"      %% "pekko-http"           % versions.pekkoHttp,
  "org.apache.pekko"      %% "pekko-http-caching"   % versions.pekkoHttp,
  "org.apache.pekko"      %% "pekko-stream"         % versions.pekko,
  "org.apache.pekko"      %% "pekko-stream-typed"   % versions.pekko,
  "org.apache.pekko"      %% "pekko-slf4j"          % versions.pekko,
  "org.apache.pekko"      %% "pekko-testkit"        % versions.pekko     % Test,
  "org.apache.pekko"      %% "pekko-stream-testkit" % versions.pekko     % Test,
  "org.apache.pekko"      %% "pekko-http-testkit"   % versions.pekkoHttp % Test,
  "com.github.pjfanning"  %% "pekko-http-json4s"    % versions.pekkoHttpJson4s,
  "org.slf4j"              % "slf4j-api"            % versions.slf4j,
  "ch.qos.logback"         % "logback-classic"      % versions.logback,
  "commons-io"             % "commons-io"           % versions.commonsio,
  "org.scalatest"         %% "scalatest"            % versions.scalatest % Test,
  "org.webjars"            % "webjars-locator"      % versions.webjarsLocator
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

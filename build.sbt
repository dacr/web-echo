name         := "web-echo"
organization := "fr.janalyse"
homepage     := Some(new URL("https://github.com/dacr/web-echo"))

licenses += "Apache 2" -> url(s"http://www.apache.org/licenses/LICENSE-2.0.txt")

scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/web-echo.git"), s"git@github.com:dacr/web-echo.git"))

Compile / mainClass    := Some("webecho.Main")
packageBin / mainClass := Some("webecho.Main")

versionScheme := Some("semver-spec")

scalaVersion := "2.13.10"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

Test / testOptions += {
  val rel = scalaVersion.value.split("[.]").take(2).mkString(".")
  Tests.Argument(
    "-oDF", // -oW to remove colors
    "-u",
    s"target/junitresults/scala-$rel/"
  )
}

lazy val versions = new {
  // client side dependencies
  val swaggerui = "4.14.2"
  val bootstrap = "5.2.2"
  val jquery    = "3.6.1"

  // server side dependencies
  val pureConfig     = "0.17.1"
  val akka           = "2.6.20"
  val akkaHttp       = "10.2.10"
  val akkaHttpJson4s = "1.39.2"
  val json4s         = "4.0.6"
  val logback        = "1.4.3"
  val slf4j          = "2.0.3"
  val scalatest      = "3.2.14"
  val commonsio      = "2.11.0"
  val webjarsLocator = "0.45"
}

// client side dependencies
libraryDependencies ++= Seq(
  "org.webjars" % "swagger-ui" % versions.swaggerui,
  "org.webjars" % "bootstrap"  % versions.bootstrap,
  "org.webjars" % "jquery"     % versions.jquery
)

// server side dependencies
libraryDependencies ++= Seq(
  "com.github.pureconfig" %% "pureconfig"          % versions.pureConfig,
  "org.json4s"            %% "json4s-jackson"      % versions.json4s,
  "org.json4s"            %% "json4s-ext"          % versions.json4s,
  "com.typesafe.akka"     %% "akka-actor-typed"    % versions.akka,
  "com.typesafe.akka"     %% "akka-http"           % versions.akkaHttp,
  "com.typesafe.akka"     %% "akka-http-caching"   % versions.akkaHttp,
  "com.typesafe.akka"     %% "akka-stream"         % versions.akka,
  "com.typesafe.akka"     %% "akka-stream-typed"   % versions.akka,
  "com.typesafe.akka"     %% "akka-slf4j"          % versions.akka,
  "com.typesafe.akka"     %% "akka-testkit"        % versions.akka      % Test,
  "com.typesafe.akka"     %% "akka-stream-testkit" % versions.akka      % Test,
  "com.typesafe.akka"     %% "akka-http-testkit"   % versions.akkaHttp  % Test,
  "de.heikoseeberger"     %% "akka-http-json4s"    % versions.akkaHttpJson4s,
  "org.slf4j"              % "slf4j-api"           % versions.slf4j,
  "ch.qos.logback"         % "logback-classic"     % versions.logback,
  "commons-io"             % "commons-io"          % versions.commonsio,
  "org.scalatest"         %% "scalatest"           % versions.scalatest % Test,
  "org.webjars"            % "webjars-locator"     % versions.webjarsLocator
)

enablePlugins(JavaServerAppPackaging)

enablePlugins(SbtTwirl)

// TODO - to remove when twirl will be available for scala3
libraryDependencies := libraryDependencies.value.map {
  case module if module.name == "twirl-api" => module.cross(CrossVersion.for3Use2_13)
  case module                               => module
}

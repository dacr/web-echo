name := "web-echo"
organization :="fr.janalyse"
homepage := Some(new URL("https://github.com/dacr/web-echo"))
licenses += "Apache 2" -> url(s"http://www.apache.org/licenses/LICENSE-2.0.txt")
scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/web-echo.git"), s"git@github.com:dacr/web-echo.git"))

mainClass in (Compile, packageBin) := Some("webecho.Main")

scalaVersion := "2.13.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

testOptions in Test += {
  val rel = scalaVersion.value.split("[.]").take(2).mkString(".")
  Tests.Argument(
    "-oDF", // -oW to remove colors
    "-u", s"target/junitresults/scala-$rel/"
  )
}

lazy val versions = new {
  // client side dependencies
  val swaggerui        = "3.43.0"
  val bootstrap        = "4.6.0"
  val jquery           = "3.5.1"

  // server side dependencies
  val pureConfig       = "0.14.0"
  val akka             = "2.6.13"
  val akkaHttp         = "10.2.4"
  val akkaHttpJson4s   = "1.35.3"
  val json4s           = "3.6.10"
  val logback          = "1.2.3"
  val slf4j            = "1.7.30"
  val scalatest        = "3.2.5"
  val commonsio        = "2.8.0"
  val webjarsLocator   = "0.40"
  val yamusca          = "0.8.0"
}

// client side dependencies
libraryDependencies ++= Seq(
  "org.webjars" % "swagger-ui" % versions.swaggerui,
  "org.webjars" % "bootstrap" % versions.bootstrap,
  "org.webjars" % "jquery"    % versions.jquery,
)

// server side dependencies
libraryDependencies ++= Seq(
  "com.github.pureconfig"  %% "pureconfig"          % versions.pureConfig,
  "org.json4s"             %% "json4s-jackson"       % versions.json4s,
  "org.json4s"             %% "json4s-ext"          % versions.json4s,
  "com.typesafe.akka"      %% "akka-http"           % versions.akkaHttp,
  "com.typesafe.akka"      %% "akka-http-caching"   % versions.akkaHttp,
  "com.typesafe.akka"      %% "akka-stream"         % versions.akka,
  "com.typesafe.akka"      %% "akka-slf4j"          % versions.akka,
  "com.typesafe.akka"      %% "akka-testkit"        % versions.akka % Test,
  "com.typesafe.akka"      %% "akka-stream-testkit" % versions.akka % Test,
  "com.typesafe.akka"      %% "akka-http-testkit"   % versions.akkaHttp % Test,
  "de.heikoseeberger"      %% "akka-http-json4s"    % versions.akkaHttpJson4s,
  "org.slf4j"              %  "slf4j-api"           % versions.slf4j,
  "ch.qos.logback"         %  "logback-classic"     % versions.logback,
  "commons-io"             %  "commons-io"          % versions.commonsio,
  "org.scalatest"          %% "scalatest"           % versions.scalatest % Test,
  "org.webjars"            %  "webjars-locator"     % versions.webjarsLocator,
  "com.github.eikek"       %% "yamusca-core"        % versions.yamusca,
)

enablePlugins(JavaServerAppPackaging)


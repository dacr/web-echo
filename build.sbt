name := "web-echo"
organization :="fr.janalyse"
homepage := Some(new URL("https://github.com/dacr/lorem-ipsum-server-akkahttp"))
licenses += "Apache 2" -> url(s"http://www.apache.org/licenses/LICENSE-2.0.txt")
scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/lorem-ipsum-server-akkahttp.git"), s"git@github.com:dacr/lorem-ipsum-server-akkahttp.git"))

scalaVersion := "2.13.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

testOptions in Test += {
  val rel = scalaVersion.value.split("[.]").take(2).mkString(".")
  Tests.Argument(
    "-oDF", // -oW to remove colors
    "-u", s"target/junitresults/scala-$rel/"
  )
}

lazy val versions = new {
  // server side dependencies
  val pureConfig       = "0.14.0"
  val akka             = "2.6.10"
  val akkaHttp         = "10.2.1"
  val akkaHttpJson4s   = "1.35.2"
  val json4s           = "3.6.10"
  val logback          = "1.2.3"
  val slf4j            = "1.7.30"
  val scalatest        = "3.2.3"
}

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
  "org.scalatest"          %% "scalatest"           % versions.scalatest % Test,
)

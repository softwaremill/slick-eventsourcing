import sbt._
import Keys._

import scalariform.formatter.preferences._

val slickVersion = "3.1.1"
val akkaVersion = "2.4.8"

val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.21"
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"
val json4s = "org.json4s" %% "json4s-native" % "3.4.0"
val slick = "com.typesafe.slick" %% "slick" % slickVersion
val tagging = "com.softwaremill.common" %% "tagging" % "1.0.0"
val idGenerator = "com.softwaremill.common" %% "id-generator" % "1.1.0"

// testing/example
val scalatest = "org.scalatest" %% "scalatest" % "3.0.0" % "test"
val h2 = "com.h2database" % "h2" % "1.3.176"// 1.4.190 is beta
val flyway = "org.flywaydb" % "flyway-core" % "4.0.3"
val slickHikari = "com.typesafe.slick" %% "slick-hikaricp" % slickVersion
val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.7"

// example only
val typesafeConfig = "com.typesafe" % "config" % "1.3.0"
val akkaHttp = "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion
val akkaHttpSession = "com.softwaremill.akka-http-session" %% "core" % "0.2.6"
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion

name := "slick-eventsourcing"

// factor out common settings into a sequence
lazy val commonSettings = scalariformSettings ++ Seq(
  organization := "com.softwaremill.events",
  version := "0.1.7-SNAPSHOT",
  scalaVersion := "2.11.8",

  scalacOptions ++= Seq("-unchecked", "-deprecation"),

  parallelExecution := false,

  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(CompactControlReadability, true)
    .setPreference(SpacesAroundMultiImports, false),

  // Sonatype OSS deployment
  publishTo <<= version { (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  credentials   += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra :=
    <scm>
      <url>git@github.com:adamw/macwire.git</url>
      <connection>scm:git:git@github.com:adamw/macwire.git</connection>
    </scm>
      <developers>
        <developer>
          <id>adamw</id>
          <name>Adam Warski</name>
          <url>http://www.warski.org</url>
        </developer>
      </developers>,
  licenses      := ("Apache2", new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil,
  homepage      := Some(new java.net.URL("http://www.softwaremill.com"))
)

lazy val slickEventsourcing = (project in file("."))
  .settings(commonSettings)
  .settings(publishArtifact := false)
  .aggregate(core, example)

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      slf4jApi, scalaLogging,
      slick, tagging, idGenerator, json4s,
      scalatest, flyway % "test", h2 % "test", logbackClassic % "test")
  )

lazy val example = (project in file("example"))
  .settings(commonSettings)
  .settings(Revolver.settings)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      akkaActor, akkaHttp, akkaSlf4j, akkaHttpSession,
      scalatest, slickHikari, flyway, h2,
      logbackClassic, typesafeConfig),
    mainClass in Compile := Some("com.softwaremill.example.Main"),
    assemblyJarName in assembly := "app.jar"
  ) dependsOn (core)
import sbt._
import Keys._

import scalariform.formatter.preferences._

val slickVersion = "3.1.0"
val akkaHttpVersion = "2.0-M1"

val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.13"
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
val json4s = "org.json4s" %% "json4s-native" % "3.3.0"
val slick = "com.typesafe.slick" %% "slick" % slickVersion
val tagging = "com.softwaremill.common" %% "tagging" % "1.0.0"
val idGenerator = "com.softwaremill.common" %% "id-generator" % "1.0.0"

// testing/example
val scalatest = "org.scalatest" %% "scalatest" % "2.2.5" % "test"
val h2 = "com.h2database" % "h2" % "1.3.176"
val flyway = "org.flywaydb" % "flyway-core" % "3.2.1"
val slickHikari = "com.typesafe.slick" %% "slick-hikaricp" % slickVersion
val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.3"

// example only
val typesafeConfig = "com.typesafe" % "config" % "1.3.0"
val akkaHttp = "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion
val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaHttpVersion % "test"
val akkaHttpSession = "com.softwaremill.akka-http-session" %% "core" % "0.2.1"
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.4.0"
val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.4.0"

name := "slick-eventsourcing"

// factor out common settings into a sequence
lazy val commonSettings = scalariformSettings ++ Seq(
  organization := "com.softwaremill.events",
  version := "0.1.0",
  scalaVersion := "2.11.7",

  scalacOptions ++= Seq("-unchecked", "-deprecation"),

  parallelExecution := false,

  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(CompactControlReadability, true)
    .setPreference(SpacesAroundMultiImports, false)
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
      akkaActor, akkaHttp, akkaHttpTestkit, akkaSlf4j, akkaHttpSession,
      scalatest, slickHikari, flyway, h2,
      logbackClassic, typesafeConfig),
    mainClass in Compile := Some("com.softwaremill.example.Main"),
    assemblyJarName in assembly := "app.jar"
  ) dependsOn (core)
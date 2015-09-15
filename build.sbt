import sbt._
import Keys._

import scalariform.formatter.preferences._

val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.12"
val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.3"
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

val typesafeConfig = "com.typesafe" % "config" % "1.2.1"

val scalatest = "org.scalatest" %% "scalatest" % "2.2.5" % "test"

val slick = "com.typesafe.slick" %% "slick" % "3.0.0"
val h2 = "com.h2database" % "h2" % "1.3.176"
val hikari = "com.zaxxer" % "HikariCP-java6" % "2.3.8"
val flyway = "org.flywaydb" % "flyway-core" % "3.1"

val json4s = "org.json4s" %% "json4s-native" % "3.2.10"

val akkaHttpVersion = "1.0"
val akkaHttp = "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion
val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaHttpVersion % "test"
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.3.12"
val akkaHttpSession = "com.softwaremill" %% "akka-http-session" % "0.1.4"

val jodaTime = "joda-time" % "joda-time" % "2.6"
val jodaConvert = "org.joda" % "joda-convert" % "1.7"

val macwire = "com.softwaremill.macwire" %% "macros" % "1.0.5"

name := "slick-eventsourcing"

// factor out common settings into a sequence
lazy val commonSettings = scalariformSettings ++ Seq(
  organization := "com.softwaremill",
  version := "0.0.1-SNAPSHOT",
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
  .aggregate(events, example)

lazy val events = (project in file("events"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(slf4jApi, logbackClassic, scalaLogging, scalatest, typesafeConfig,
      slick, hikari, flyway, h2, jodaTime, jodaConvert, macwire, json4s)
  )

lazy val example = (project in file("example"))
  .settings(commonSettings)
  .settings(Revolver.settings)
  .settings(
    libraryDependencies ++=
      Seq(akkaHttp, akkaHttpTestkit, akkaSlf4j, akkaHttpSession, scalatest),
    mainClass in Compile := Some("com.softwaremill.example.Main"),
    assemblyJarName in assembly := "app.jar"
  ) dependsOn (events)
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"

val AkkaVersion = "2.6.20"
val AkkaHttpVersion = "10.2.10"

lazy val commonSettings = Seq(
  libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  libraryDependencies += "org.apache.kafka" % "kafka-clients" % "3.3.1",
  libraryDependencies += "org.apache.kafka" % "kafka-streams" % "3.3.1",
  libraryDependencies += "org.apache.kafka" %% "kafka-streams-scala" % "3.3.1",
//  libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.4",
  libraryDependencies += "com.loopfor.zookeeper" %% "zookeeper-client" % "1.6",
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.11"
//  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.9"
//  libraryDependencies += "log4j" % "log4j" % "1.2.14"
//  libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.36",
//  libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.36"
//  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.36" % Test
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "sandbox_akka"
//    idePackagePrefix := Some("claudiostahl")
  )

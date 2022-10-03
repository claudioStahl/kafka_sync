ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"

val AkkaVersion = "2.6.20"
val AkkaHttpVersion = "10.2.10"

lazy val commonSettings = Seq(
  libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  libraryDependencies += "org.apache.kafka" % "kafka-clients" % "3.3.0",
  libraryDependencies += "org.apache.kafka" % "kafka-streams" % "3.3.0",
  libraryDependencies += "org.slf4j" % "slf4j-api" % "2.0.3",
  libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "2.0.3" % Test pomOnly()
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "claudiostahl.sandbox_akka"
//    idePackagePrefix := Some("claudiostahl")
  )

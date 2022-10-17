ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"

val AkkaVersion = "2.6.20"
val AkkaHttpVersion = "10.2.10"
val circeVersion = "0.14.1"

lazy val commonSettings = Seq(
  resolvers += "jitpack".at("https://jitpack.io"),

  libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  libraryDependencies += "org.apache.kafka" % "kafka-clients" % "3.3.1",
  libraryDependencies += "org.apache.kafka" % "kafka-streams" % "3.3.1",
  libraryDependencies += "org.apache.kafka" %% "kafka-streams-scala" % "3.3.1",
  libraryDependencies += "com.loopfor.zookeeper" %% "zookeeper-client" % "1.6",

  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.11",
  libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",

  libraryDependencies += "io.circe" %% "circe-core" % circeVersion,
  libraryDependencies += "io.circe" %% "circe-generic" % circeVersion,
  libraryDependencies += "io.circe" %% "circe-parser" % circeVersion,
  libraryDependencies += "io.circe" %% "circe-optics" % circeVersion,
  libraryDependencies += "io.circe" %% "circe-literal" % circeVersion,
  libraryDependencies += "com.goyeau" %% "kafka-streams-circe" % "0.6.3",
  libraryDependencies += "io.circe" %% "circe-json-schema" % "0.1.0"
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "sandbox_akka"
//    idePackagePrefix := Some("claudiostahl")
  )

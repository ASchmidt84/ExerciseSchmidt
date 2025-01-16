import com.typesafe.sbt.packager.docker.{DockerChmodType, DockerPermissionStrategy}

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

scalaVersion := "2.13.16"

organization := "schmidt.andre"

dockerBaseImage := "docker.io/library/eclipse-temurin:21.0.1_12-jre-jammy"
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
ThisBuild / dynverSeparator := "-"

lazy val buildVersion = ""

Compile / scalacOptions ++= Seq(
  "-target:11",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")
Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

Test / parallelExecution := false
Test / testOptions += Tests.Argument("-oDF")
Test / logBuffered := false

run / fork := true
// pass along config selection to forked jvm
run / javaOptions ++= sys.props
  .get("config.resource")
  .fold(Seq.empty[String])(res => Seq(s"-Dconfig.resource=$res"))

Global / cancelable := false // ctrl-c

val AkkaVersion = "2.9.4"
val AkkaHttpVersion = "10.6.3"
val AkkaManagementVersion = "1.5.2"
val AkkaPersistenceJdbcVersion = "5.4.1"
val AlpakkaKafkaVersion = "6.0.0"
val AkkaProjectionVersion = "1.5.4"
val AkkaDiagnosticsVersion = "2.1.1"
val ScalikeJdbcVersion = "4.3.2"

val defaultResolvers = Seq(
  "Akka library repository".at("https://repo.akka.io/maven")
)

val defaultSettings = Seq(
  Compile / scalacOptions ++= Seq(
    "-target:11",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlog-reflective-calls",
    "-Xlint"),
  Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
  Test / parallelExecution := false,
  Test / testOptions += Tests.Argument("-oDF"),
  Test / logBuffered := false,
  run / fork := true,
  // pass along config selection to forked jvm
  run / javaOptions ++= sys.props.get("config.resource")
    .fold(Seq.empty[String])(res => Seq(s"-Dconfig.resource=$res")),
  Global / cancelable := false, // ctrl-c
  dockerBaseImage := "docker.io/library/eclipse-temurin:21.0.1_12-jre-jammy",
  dockerChmodType := DockerChmodType.UserGroupWriteExecute,
  dockerPermissionStrategy := DockerPermissionStrategy.CopyChown,
  dockerUsername := sys.props.get("docker.username"),
  dockerRepository := sys.props.get("docker.registry"),
  scalaVersion := "2.13.16",
  ThisBuild / dynverSeparator := "-",
  version := buildVersion
)

val defaultLibs = Seq(
  // 1. Basic dependencies for a clustered application
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  // Akka Management powers Health Checks, Akka Cluster Bootstrapping, and Akka Diagnostics
  "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaManagementVersion,
  "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
  "com.lightbend.akka" %% "akka-diagnostics" % AkkaDiagnosticsVersion,
  // Common dependencies for logging and testing
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.5.16",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  // 2. Using Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "org.postgresql" % "postgresql" % "42.7.4",
  // 3. Querying or projecting data from Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-jdbc" % AkkaProjectionVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.lightbend.akka" %% "akka-projection-r2dbc" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-grpc" % AkkaProjectionVersion,

  "org.scalikejdbc" %% "scalikejdbc" % ScalikeJdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-config" % ScalikeJdbcVersion,
  "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
  "com.lightbend.akka" %% "akka-projection-testkit" % AkkaProjectionVersion % Test)

lazy val root = (project in file("."))
  .settings(
    name := "ExerciseSchmidt",
    defaultSettings,
    libraryDependencies ++= defaultLibs,
    resolvers ++= defaultResolvers
  )
  .enablePlugins(AkkaGrpcPlugin, JavaAppPackaging, DockerPlugin)

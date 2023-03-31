ThisBuild / organization := "se.su.dsv"

ThisBuild / version := "1.0"

ThisBuild / scalaVersion := "3.2.1"

val http4sVersion = "0.23.18"

ThisBuild / libraryDependencySchemes += "org.http4s" %% "http4s-core" % "always"

def http4s(module: String, version: String = http4sVersion): ModuleID = "org.http4s" %% s"http4s-$module" % version

ThisBuild / libraryDependencies ++= Seq(
  "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  http4s("blaze-server", "0.23.13"),
  http4s("dsl"),
  http4s("circe"),
  http4s("servlet", "0.23.13"),
  http4s("twirl", "0.24.0-M1"),
  "org.tpolecat" %% "doobie-core"      % "1.0.0-RC2",
  "org.tpolecat" %% "doobie-scalatest" % "1.0.0-RC2" % Test,
  "com.h2database" % "h2" % "2.1.214" % Test,
  "org.slf4j" % "slf4j-simple" % "1.7.22",
  "org.flywaydb" % "flyway-core" % "9.16.0",
  "org.flywaydb" % "flyway-mysql" % "9.16.0"
)

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",       // yes, this is 2 args
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
)

lazy val toker = project.in(file("."))
  .dependsOn(core, production, staging, dev)
  .aggregate(core, production, staging, dev)
  .settings(
    Keys.`package` / aggregate := false
  )

lazy val core = project.in(file("core"))
  .enablePlugins(SbtTwirl)
  .settings(
    name := "core",
    TwirlKeys.templateImports := Seq() // required due to -Xfatal-warnings
  )

lazy val production = project.in(file("production"))
  .dependsOn(core)
  .enablePlugins(WarPlugin)
  .settings(
    name := "production"
  )

lazy val staging = project.in(file("staging"))
  .dependsOn(core)
  .enablePlugins(WarPlugin)
  .enablePlugins(SbtTwirl)
  .settings(
    name := "staging",
    TwirlKeys.templateImports := Seq() // required due to -Xfatal-warnings
  )

lazy val dev = project.in(file("dev"))
  .dependsOn(staging)
  .enablePlugins(JettyPlugin)
  .settings(
    name := "dev",
    Jetty / containerArgs := Seq("--config", "jetty.xml"),
    Jetty / containerLibs ++= Seq("org.mariadb.jdbc" % "mariadb-java-client" % "3.1.3"),
    webappPostProcess := {
      webappDir =>
        val stagingWebapp = (staging / Compile / webappPrepare / sourceDirectory).value / "webapp"
        IO.copyDirectory(stagingWebapp, webappDir)
    }
  )

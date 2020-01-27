ThisBuild / organization := "se.su.dsv"

ThisBuild / version := "1.0"

ThisBuild / scalaVersion := "2.12.10"

val http4sVersion = "0.20.13"

def http4s(module: String): ModuleID = "org.http4s" %% s"http4s-$module" % http4sVersion

ThisBuild / libraryDependencies ++= Seq(
  "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  http4s("blaze-server"),
  http4s("dsl"),
  http4s("argonaut"),
  http4s("servlet"),
  http4s("twirl"),
  "org.tpolecat" %% "doobie-core"      % "0.7.1",
  "org.tpolecat" %% "doobie-scalatest" % "0.7.1" % Test,
  "com.h2database" % "h2" % "1.4.193" % Test,
  "org.slf4j" % "slf4j-simple" % "1.7.22",
  "org.flywaydb" % "flyway-core" % "4.0.3",
  compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")
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
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",        // N.B. doesn't work well with the ??? hole
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Ypartial-unification"
  //"-Ywarn-unused-import",    // 2.11 only
)

ThisBuild / resolvers += Resolver.sonatypeRepo("releases")

lazy val toker = project.in(file("."))
  .dependsOn(core, production, staging, dev)
  .aggregate(core, production, staging, dev)
  .settings(
    Keys.`package` / aggregate := false
  )

lazy val core = project.in(file("core"))
  .settings(
    name := "core"
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
    libraryDependencies ++= Seq(
      "io.opentracing.contrib" % "opentracing-web-servlet-filter" % "0.3.0",
      "io.jaegertracing" % "jaeger-client" % "1.1.0"
    ),
    TwirlKeys.templateImports := Seq() // required due to -Xfatal-warnings
  )

lazy val dev = project.in(file("dev"))
  .dependsOn(staging)
  .enablePlugins(JettyPlugin)
  .settings(
    name := "dev",
    containerArgs in Jetty := Seq("--config", "jetty.xml"),
    containerLibs in Jetty ++= Seq("mysql" % "mysql-connector-java" % "5.1.40"),
    webappPostProcess := {
      webappDir =>
        val stagingWebapp = (staging / sourceDirectory in webappPrepare in Compile).value / "webapp"
        IO.copyDirectory(stagingWebapp, webappDir)
    }
  )

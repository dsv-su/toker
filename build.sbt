name := "toker"

organization := "se.su.dsv"

version := "1.0"

scalaVersion := "2.12.10"

val http4sVersion = "0.20.13"

def http4s(module: String): ModuleID = "org.http4s" %% s"http4s-$module" % http4sVersion

libraryDependencies ++= Seq(
  "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  http4s("blaze-server"),
  http4s("dsl"),
  http4s("argonaut"),
  http4s("servlet"),
  "org.tpolecat" %% "doobie-core"      % "0.7.1",
  "org.tpolecat" %% "doobie-scalatest" % "0.7.1" % Test,
  "com.h2database" % "h2" % "1.4.193" % Test,
  "org.slf4j" % "slf4j-simple" % "1.7.22",
  "org.flywaydb" % "flyway-core" % "4.0.3"
)

scalacOptions ++= Seq(
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

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

enablePlugins(JettyPlugin)

containerArgs in Jetty := Seq("--config", "jetty.xml")

containerLibs in Jetty ++= Seq(
  "mysql" % "mysql-connector-java" % "5.1.40"
)

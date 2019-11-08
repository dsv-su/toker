name := "toker"

organization := "se.su.dsv"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies ++= Seq(
  "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  "org.http4s" %% "http4s-blaze-server" % "0.15.3a",
  "org.http4s" %% "http4s-dsl"          % "0.15.3a",
  "org.http4s" %% "http4s-argonaut"     % "0.15.3a",
  "org.http4s" %% "http4s-servlet"      % "0.15.3a",
  "org.tpolecat" %% "doobie-core"      % "0.4.1",
  "org.tpolecat" %% "doobie-scalatest" % "0.4.1" % Test,
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

containerConfigFile := Some(file("jetty.xml"))

containerLibs in Jetty ++= Seq(
  "mysql" % "mysql-connector-java" % "5.1.40"
)

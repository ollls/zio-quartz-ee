import Dependencies._

ThisBuild / scalaVersion     := "2.13.4"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "ged-server",
    libraryDependencies ++= Seq( 
       "io.github.ollls"  %% "zio-tls-http" % "1.2-m1",
       "io.github.kitlangton" %% "zio-magic" % "0.2.0",
        "com.unboundid" % "unboundid-ldapsdk" % "5.1.2"
    )
   )

   scalacOptions ++= Seq(
  "-Xfatal-warnings",
  "-deprecation",
)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.

val ZioVersion    = "1.0.0-RC13"
val Specs2Version = "4.7.0"

resolvers += Resolver.sonatypeRepo("releases")
resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += Resolver.bintrayRepo("zamblauskas", "maven")

lazy val root = (project in file("."))
  .settings(
    organization := "ZIO",
    name := "zio-awesome-project",
    version := "0.0.1",
    scalaVersion := "2.12.9",
    maxErrors := 3,
    libraryDependencies ++= Seq(
      "dev.zio"    %% "zio"         % ZioVersion,
      "org.specs2" %% "specs2-core" % Specs2Version % "test",
      "zamblauskas" %% "scala-csv-parser" % "0.11.6",
      "io.jenetics" % "jpx" % "1.6.0",
      "net.ruippeixotog" %% "scala-scraper" % "2.2.0",
      "com.github.pathikrit" %% "better-files" % "3.8.0",
      "com.lihaoyi" %% "upickle" % "0.8.0",
      "com.fasterxml.jackson.core" % "jackson-core" % "2.10.1",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.10.1",
        "com.fasterxml.jackson.module" % "jackson-module-scala_2.12" % "2.10.1"
)
  )

// Refine scalac params from tpolecat
scalacOptions --= Seq(
  "-Xfatal-warnings"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("chk", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

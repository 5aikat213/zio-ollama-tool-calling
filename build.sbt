ThisBuild / scalaVersion := "3.3.3"
ThisBuild / version      := "0.1.0-SNAPSHOT"

val zioVersion     = "2.1.17"
val zioHttpVersion = "3.3.3"
val zioJsonVersion = "0.7.42"

lazy val root = (project in file("."))
  .settings(
    name := "zio-ollama-chat",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"              % zioVersion,
      "dev.zio" %% "zio-streams"      % zioVersion,
      "dev.zio" %% "zio-http"         % zioHttpVersion,
      "dev.zio" %% "zio-json"         % zioJsonVersion,
      "dev.zio" %% "zio-logging"      % "2.5.0"
    )
  )
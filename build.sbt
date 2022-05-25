val scala3Version = "3.1.2"

val jodaMoneyDependencies = Seq(
  "org.joda" % "joda-money" % "1.0.1"
)

val catsDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.7.0",
  "org.typelevel" %% "cats-effect" % "3.3.11"
)

val loggerDependencies = Seq(
  "org.typelevel" %% "log4cats-slf4j" % "2.3.0",
  "org.slf4j" % "slf4j-simple" % "1.7.36"
)

val http4sVersion = "0.23.11"
val http4sDependencies = Seq(
  "org.http4s" %% "http4s-dsl",
  "org.http4s" %% "http4s-ember-server",
  "org.http4s" %% "http4s-ember-client",
  "org.http4s" %% "http4s-blaze-server",
  "org.http4s" %% "http4s-blaze-client",
  "org.http4s" %% "http4s-circe"
).map(_ % http4sVersion)

val circeVersion = "0.14.1"

val circeDependencies = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

val munitDependencies = Seq(
  "org.scalameta" %% "munit" % "0.7.29" % Test
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "ematiq-converter",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++=
      loggerDependencies ++
        jodaMoneyDependencies ++
        catsDependencies ++
        http4sDependencies ++
        circeDependencies ++
        munitDependencies,
    testFrameworks += new TestFramework("munit.Framework"),
    logBuffered := false,
    Test / parallelExecution := false
  )

import Dependencies._

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  // "-no-indent",
  "-deprecation", // Warns about deprecated APIs
  "-feature", // Warns about advanced language features
  "-unchecked",
  // "-Wunused:imports",
  //   "-Wunused:privates",
  //   "-Wunused:locals",
  //   "-Wunused:explicits",
  //   "-Wunused:implicits",
  //   "-Wunused:params",
  //   "-Wvalue-discard",
  // "-language:strictEquality",
  "-Xmax-inlines:100000"
)

lazy val Version = new {
  val catsEffect = "3.6.3"
  val http4s = "0.23.33"
  val tapir = "1.10.6"
  val jsoniter = "2.38.4"
  val sttp4 = "4.0.13"
  val doobie = "1.0.0-RC5"
  val flyway = "10.18.2"
  val pureconfig = "0.17.6"
  val logback = "1.5.6"
  val slf4j = "2.0.16"
  val scalacheck = "1.17.0"
  val fs2 = "3.12.2"
  val scribe = "3.17.0"

}
def http4s(artifact: String): ModuleID =
  "org.http4s" %% s"http4s-$artifact" % Version.http4s

def tapir(artifact: String): ModuleID =
  "com.softwaremill.sttp.tapir" %% s"tapir-$artifact" % Version.tapir

def sttp(artifact: String): ModuleID =
  "com.softwaremill.sttp.client4" %% artifact % Version.sttp4

val dsl = http4s("dsl")
val emberServer = http4s("ember-server")
val emberClient = http4s("ember-client")

val sttpCore = sttp("core")
val sttpFs2 = sttp("fs2")
val sttpCats = sttp("cats")
val sttpCirce = sttp("circe")
val sttpJsoniter = sttp("jsoniter")
val sttpSlf4j = sttp("slf4j-backend")
// https://mvnrepository.com/artifact/com.softwaremill.sttp.client4/async-http-client-backend-fs2
val clientBackendFs2 = sttp("async-http-client-backend-fs2")
val http4sBackend = sttp("http4s-backend")

val sttpOkHttpBackend = sttp("okhttp-backend")
val sttpPrometheusBackend = sttp("prometheus-backend")
val sttpScribeBackend = sttp("scribe-backend")
val generate = taskKey[Unit]("generate code from APIs")

lazy val root = (project in file("."))
  .settings(
    name := "authlete",
    libraryDependencies ++= Seq(
      sttpCore,
      sttpJsoniter,
      http4sBackend,
      dsl,
      emberServer,
      "com.outr" %% "scribe" % "3.16.1",
      "com.outr" %% "scribe-slf4j" % "3.16.1",
      "com.outr" %% "scribe-cats" % "3.16.1",
      "org.http4s" %% "http4s-ember-client" % Version.http4s,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % Version.jsoniter,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Version.jsoniter % "provided",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe" % Version.jsoniter,
      "org.typelevel" %% "cats-effect" % Version.catsEffect,
      "com.github.pureconfig" %% "pureconfig-core" % Version.pureconfig,
      "org.slf4j" % "slf4j-api" % Version.slf4j,
      "ch.qos.logback" % "logback-classic" % Version.logback % Runtime,
      "co.fs2" %% "fs2-core" % Version.fs2,
      munit % Test,
      "qa.hedgehog" %% "hedgehog-sbt" % "0.13.0" % Test,
      "qa.hedgehog" %% "hedgehog-core" % "0.13.0" % Test,
      "qa.hedgehog" %% "hedgehog-runner" % "0.13.0" % Test
    )
  )
.dependsOn(`authlete-codegen` % "compile->compile")

lazy val `authlete-codegen` = (project in file("modules/authlete-codegen"))
  .enablePlugins(OpenApiGeneratorPlugin)
  .settings(
    name := "authlete-codegen",
    // openApiInputSpec := "src/main/resources/swagger.json",
    // openApiGeneratorName := "sclala-sttp-client4",
    openApiModelNamePrefix := "",
    openApiModelNameSuffix := "",
    openApiSkipOverwrite := Some(false),
    openApiRemoveOperationIdPrefix := Some(true),
    openApiGenerateMetadata := Some(false),
    openApiGenerateMetadata := SettingDisabled,
    // Use the same JSON so CLI and SBT stay in sync
    openApiConfigFile := ((Compile / baseDirectory).value / "config.json").getPath,
    openApiIgnoreFileOverride := s"${baseDirectory.value.getPath}/openapi-ignore-file",

    // Put generated sources where SBT expects managed sources
    openApiOutputDir := ((Compile / baseDirectory).value / "src/main/scala").getAbsolutePath,
    openApiGenerateModelTests := SettingDisabled,
    openApiGenerateApiTests := SettingDisabled,
    openApiValidateSpec := SettingDisabled,
    // Fail fast on bad specs (optional but recommended)
    openApiValidateSpec := Some(true),
    // Compile / sourceGenerators += openApiGenerate.taskValue,
    (Compile / compile) := ((Compile / compile) dependsOn generate).value,
    // (Compile/compile) := ((compile in Compile) dependsOn openApiGenerate).value

    // Define the simple generate command to generate full client codes
    generate := {
      val _ = openApiGenerate.value

      // Delete the generated build.sbt file so that it is not used for our sbt config
      val buildSbtFile = file(openApiOutputDir.value) / "build.sbt"
      if (buildSbtFile.exists()) {
        buildSbtFile.delete()
      }
    },
    libraryDependencies ++= Seq(
      sttpJsoniter,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % Version.jsoniter,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Version.jsoniter % "provided",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe" % Version.jsoniter
    )
  )

lazy val populateTestDB =
  taskKey[Unit]("Run PopulateTestDatabase main class from the test folder")

populateTestDB := {
  val log = streams.value.log
  (Test / runMain)
    .toTask(s"utils.PopulateTestDatabase")
    .value
}

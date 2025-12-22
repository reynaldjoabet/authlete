import Dependencies._

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
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

val generate = taskKey[Unit]("generate code from APIs")

lazy val root = (project in file("."))
  .settings(
    name := "authlete",
    libraryDependencies ++= Seq(
      sttpCore,
      sttpJsoniter,
      http4sBackend,
      `http4s-dsl`,
      emberServer,
      chimney,
      fs2,
      emberClient,
      catsEffect,
      pureconfig,
      slf4j,
      logback,
      scribe,
      scribeSlf4j,
      scribeCats,
      jsoniter,
      jsoniterMacros,
      jsoniterCirce,
      munit,
      "qa.hedgehog" %% "hedgehog-sbt" % "0.13.0" % Test,
      "qa.hedgehog" %% "hedgehog-core" % "0.13.0" % Test,
      "qa.hedgehog" %% "hedgehog-runner" % "0.13.0" % Test
    )
  )
  .dependsOn(`authlete-codegen` % "compile->compile")
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion
    ),
    buildInfoPackage := "authlete",
    buildInfoObject := "AuthleteBuildInfo",
    scalacOptions ++= Seq("-no-indent")
  )

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

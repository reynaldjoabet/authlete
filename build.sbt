import Dependencies.*

ThisBuild / scalaVersion := "3.3.8"
version                  := "0.1.0-SNAPSHOT"

val generate = taskKey[Unit]("generate code from APIs")

lazy val root = (project in file("."))
  .settings(
    name := "authlete",
    // Scoped to root only: authlete-codegen's generated sources don't carry
    // CanEqual givens for their enums, so -language:strictEquality would fail
    // there. (Top-level scalacOptions apply to every subproject in sbt 2.x,
    // unlike sbt 1.x where they were root-only, so this must stay scoped here.)
    scalacOptions ++= Seq(
      "-deprecation", // Warns about deprecated APIs
      "-feature",     // Warns about advanced language features
      "-unchecked",
      // "-Wunused:imports",
      //   "-Wunused:privates",
      //   "-Wunused:locals",
      //   "-Wunused:explicits",
      //   "-Wunused:implicits",
      //   "-Wunused:params",
      //   "-Wvalue-discard",
      "-language:strictEquality",
      "-Xmax-inlines:100000"
    ),
    libraryDependencies ++= Seq(
      sttpCore,
      sttpJsoniter,
      http4sBackend,
      http4sDsl,
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
      nimbusJoseJwt,
      nimbusOauth2Oidc,
      jwtCirce,
      caffeine,
      zio,
      zioJson,
      zioTest,
      zioTestSbt,
      zioConfig,
      zioConfigMagnolia,
      zioLogging,
      zioLoggingSlf4j,
      zioHttp,
      zioJsonGolden,
      zioSttp,
      zioKafka,
      circeParser,
      "qa.hedgehog" %% "hedgehog-sbt"    % "0.13.0" % Test,
      "qa.hedgehog" %% "hedgehog-core"   % "0.13.0" % Test,
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
    buildInfoObject  := "AuthleteBuildInfo",
    scalacOptions   ++= Seq("-no-indent")
  )

lazy val `authlete-codegen` = (project in file("modules/authlete-codegen"))
  .enablePlugins(OpenApiGeneratorPlugin)
  .settings(
    name := "authlete-codegen",
    // openApiInputSpec := "src/main/resources/swagger.json",
    // openApiGeneratorName := "sclala-sttp-client4",
    openApiModelNamePrefix         := "",
    openApiModelNameSuffix         := "",
    openApiSkipOverwrite           := Some(false),
    openApiRemoveOperationIdPrefix := Some(true),
    openApiGenerateMetadata        := SettingDisabled,
    // Use the same JSON so CLI and SBT stay in sync
    openApiConfigFile         := ((Compile / baseDirectory).value / "config.json").getPath,
    openApiIgnoreFileOverride := s"${baseDirectory.value.getPath}/openapi-ignore-file",

    // Put generated sources where SBT expects managed sources
    openApiOutputDir          := ((Compile / baseDirectory).value / "src/main/scala").getAbsolutePath,
    openApiGenerateModelTests := SettingDisabled,
    openApiGenerateApiTests   := SettingDisabled,
    openApiValidateSpec       := SettingDisabled,
    // Fail fast on bad specs (optional but recommended)
    openApiValidateSpec := Some(true),
    // Compile / sourceGenerators += openApiGenerate.taskValue,
    (Compile / compile) := Def.uncached {
      (Compile / compile).dependsOn(generate).value
    },
    // (Compile/compile) := ((compile in Compile) dependsOn openApiGenerate).value

    // Define the simple generate command to generate full client codes.
    // Wrapped in Def.uncached so sbt 2's task cache never skips this
    // side-effectful file generation.
    generate := Def.uncached {
      Def
        .task {
          val _ = openApiGenerate.value

          // Delete the generated build.sbt file so that it is not used for our sbt config
          val buildSbtFile = file(openApiOutputDir.value) / "build.sbt"
          if (buildSbtFile.exists()) {
            buildSbtFile.delete()
          }
        }
        .value
    },
    libraryDependencies ++= Seq(
      sttpJsoniter,
      jsoniter,
      jsoniterMacros,
      jsoniterCirce
    )
  )

lazy val populateTestDB =
  taskKey[Unit]("Run PopulateTestDatabase main class from the test folder")

populateTestDB := Def.uncached {
  Def
    .task {
      val log = streams.value.log
      (Test / runMain).toTask(s"utils.PopulateTestDatabase").value
    }
    .value
}

Global / onChangedBuildSource := IgnoreSourceChanges

ThisProject / dependencyOverrides += "dev.zio" %% "zio-json" % "0.9.0"

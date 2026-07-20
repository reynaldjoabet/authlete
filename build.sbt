import Dependencies.*

ThisBuild / scalaVersion := "3.3.8"
ThisBuild / version      := "0.1.0-SNAPSHOT"

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
      hedgehog,
      hedgehogSbt,
      hedgehogRunner
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

    // Generated code lands under src/main/scala/authlete (see config.json,
    // shared with the CLI so both stay in sync).
    openApiOutputDir          := ((Compile / baseDirectory).value / "src/main/scala").getAbsolutePath,
    openApiGenerateModelTests := SettingDisabled,
    openApiGenerateApiTests   := SettingDisabled,
    // Fail fast on bad specs (optional but recommended)
    openApiValidateSpec := Some(true),

    // Feed the generated sources into compilation via sourceGenerators rather
    // than `compile.dependsOn(generate)`: the latter only orders generation
    // before compile's body, not before sbt discovers sources, so a clean build
    // (e.g. CI) could compile codegen with an empty source list and leave the
    // authlete.* packages off the classpath. sourceGenerators makes generation
    // a proper input to `sources`, so sbt always runs it first.
    Compile / sourceGenerators += Def
      .task {
        generate.value
        (file(openApiOutputDir.value) ** "*.scala").get()
      }
      .taskValue,
    // All of codegen's Scala is generated (under authlete/); drop the unmanaged
    // source dir so the generated files are compiled once, via the generator
    // above, instead of also being globbed as unmanaged sources.
    Compile / unmanagedSourceDirectories := Seq.empty,

    // Manual entry point to (re)generate the client without a full compile.
    //
    // Must stay uncached. sbt 2 caches `:=` task results by default, but the
    // cache key is built from the task's `.value` inputs, and nothing here
    // hashes the OpenAPI spec's *contents* -- sbt's own file-input keys
    // (allInputFiles / changedInputFiles) are @transient, i.e. deliberately
    // excluded from cache input. A Def.cachedTask would therefore keep serving
    // a stale client whenever the spec changed, so we always regenerate.
    generate := Def.uncached {
      val _ = openApiGenerate.value

      // Delete the generated build.sbt file so that it is not used for our sbt config
      val buildSbtFile = file(openApiOutputDir.value) / "build.sbt"
      if (buildSbtFile.exists()) {
        buildSbtFile.delete()
      }
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
  val log = streams.value.log
  (Test / runMain).toTask(s"utils.PopulateTestDatabase").value
}

Global / onChangedBuildSource := IgnoreSourceChanges

ThisProject / dependencyOverrides += "dev.zio" %% "zio-json" % "0.9.2"

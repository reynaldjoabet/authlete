import Dependencies.*

ThisBuild / scalaVersion := "3.3.8"
ThisBuild / version      := "0.1.0-SNAPSHOT"

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
    openApiConfigFile := ((Compile / baseDirectory).value / "config.json").getPath,

    // Suppress the non-source supporting files (build.sbt, project/, README.md,
    // .scalafmt.conf) so the generator emits only .scala. The generator matches
    // ignore patterns relative to the ignore file's own directory; this one sits
    // at the module root while files land under src/main/scala, so the patterns
    // are written as **/... to match at any depth.
    openApiIgnoreFileOverride := (baseDirectory.value / ".openapi-generator-ignore").getPath,

    // Generated code lands under src/main/scala/authlete (see config.json,
    // shared with the CLI so both stay in sync).
    openApiOutputDir          := ((Compile / baseDirectory).value / "src/main/scala").getAbsolutePath,
    openApiGenerateModelTests := SettingDisabled,
    openApiGenerateApiTests   := SettingDisabled,
    // Fail fast on bad specs (optional but recommended)
    openApiValidateSpec := Some(true),

    // Wired in as a sourceGenerator, NOT as `compile.dependsOn(generate)`.
    // sbt collects `sources` by globbing src/main/scala in a task separate from
    // `compile`, and dependsOn only sequences generate ahead of `compile` --
    // not ahead of that glob. So on a clean checkout the glob would run first,
    // find nothing, and the module would compile 0 sources, leaving its
    // api/models off the classpath and failing every downstream import that
    // depends on it -- and locally you'd never notice, since the previous run's
    // files are still on disk and the glob always finds those. A sourceGenerator
    // feeds `sources` directly, so sbt has to run it first.
    //
    // No separate glob needed: generate is typed Seq[File] (see
    // Dependencies.scala), so its own return value -- the exact file list
    // openApiGenerate just wrote -- IS what sourceGenerators needs.
    Compile / sourceGenerators += generate.taskValue,
    // openApiOutputDir *is* src/main/scala, so the generator above already
    // covers everything sbt would otherwise pick up as unmanaged sources.
    // Dropping the unmanaged dir makes the generator the single source of truth
    // instead of having sbt separately glob a directory that is empty on a clean
    // checkout. Not required for correctness: `sources` is
    // (unmanaged ++ managed).distinct, so the overlap would dedupe either way.
    Compile / unmanagedSourceDirectories := Seq.empty,

    // Manual entry point to (re)generate the client without a full compile.
    //
    // Kept uncached: this task exists for its side effect (writing files), and
    // sbt 2 caches `:=` results by default, which can skip that work. The cache
    // key is built from the task's `.value` inputs, which cover setting values
    // like the spec's path but not the spec's contents -- so a cached variant
    // risks skipping regeneration after a spec edit. Always regenerating costs
    // a few seconds and avoids having to reason about it.
    generate := Def.uncached {
      openApiGenerate.value
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

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisProject / dependencyOverrides += "dev.zio" %% "zio-json" % "0.9.2"

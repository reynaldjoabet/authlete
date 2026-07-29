import Dependencies.*

ThisBuild / scalaVersion := "3.3.8"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / semanticdbEnabled := true

ThisBuild / scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-source:3.3",
  "-java-output-version:17",
  "-Werror",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Xlint:all",
  "-Ysafe-init",
  "-Xcheck-macros",
  "-Xmax-inlines:64"
)

Global / onChangedBuildSource := ReloadOnSourceChanges

val generatedScalacOptions = Seq(
  "-encoding",
  "UTF-8",
  "-java-output-version:17",
  "-Xmax-inlines:64"
)

lazy val root = (project in file("."))
  .settings(
    name                 := "authlete",
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
    buildInfoObject  := "AuthleteBuildInfo"
  )

lazy val `authlete-codegen` = (project in file("modules/authlete-codegen"))
  .enablePlugins(OpenApiGeneratorPlugin)
  .settings(
    scalacOptions                 := generatedScalacOptions,
    name                           := "authlete-codegen",
    openApiModelNamePrefix         := "",
    openApiModelNameSuffix         := "",
    openApiRemoveOperationIdPrefix := Some(true),
    openApiGenerateMetadata        := SettingDisabled,
    // Use the same JSON so CLI and SBT stay in sync
    openApiConfigFile         := ((Compile / baseDirectory).value / "config.json").getPath,
    openApiIgnoreFileOverride := (baseDirectory.value / ".openapi-generator-ignore").getPath,
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

ThisProject / dependencyOverrides += "dev.zio" %% "zio-json" % "0.9.2"

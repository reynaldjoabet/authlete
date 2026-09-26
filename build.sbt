import Dependencies.*

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / crossScalaVersions := Seq("3.9.0")

ThisBuild / scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  // "-Werror",
  // "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-language:strictEquality",
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
      munitCatsEffect,
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
  .settings(
    assembly / mainClass       := Some("Main"),
    assembly / assemblyJarName := "authlete.jar",

    // Anchored at the repository root. Left to itself, sbt 2 writes into the content-addressed
    // `target/out/jvm/<scala-version>/<project>` tree, which a Dockerfile COPY cannot name without
    // baking the Scala version into the build.
    assembly / assemblyOutputPath :=
      (LocalRootProject / baseDirectory).value / "target" / "authlete.jar",

    // Several classes under src/main/scala carry a main method (the crypto examples), so the jar's
    // Main-Class has to be stated rather than discovered -- otherwise assembly fails on the
    // ambiguity, the same way `sbt run` does.
    assembly / assemblyMergeStrategy := {
      // Typesafe Config reads *every* reference.conf on the classpath and merges them. Taking only
      // the first would silently drop the defaults of every library after it -- Pekko/Ember timeouts
      // among them -- so these have to be concatenated, not deduplicated.
      case PathList("reference.conf")   => MergeStrategy.concat
      case PathList("application.conf") => MergeStrategy.concat
      // ServiceLoader registries: same reasoning. One file per provider, all of which matter.
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      // JPMS descriptors and signatures are meaningless inside a shaded jar, and a retained
      // signature makes the JVM reject the jar as tampered with.
      case PathList("module-info.class")                                                  => MergeStrategy.discard
      case PathList("META-INF", "versions", _, "module-info.class")                       => MergeStrategy.discard
      case path if path.endsWith(".SF") || path.endsWith(".DSA") || path.endsWith(".RSA") =>
        MergeStrategy.discard
      // Build metadata, one copy per Netty module and per OSGi bundle. It describes the jar it came
      // from, so inside a shaded jar it describes nothing; no code reads it at runtime.
      case PathList("META-INF", "io.netty.versions.properties")           => MergeStrategy.discard
      case PathList("META-INF", "versions", _, "OSGI-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", "OSGI-INF", _*)                           => MergeStrategy.discard
      case path                                                           =>
        val default = (assembly / assemblyMergeStrategy).value
        default(path)
    }
  )

lazy val `authlete-codegen` = (project in file("modules/authlete-codegen"))
  .enablePlugins(OpenApiGeneratorPlugin)
  .settings(
    scalacOptions                  := generatedScalacOptions,
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

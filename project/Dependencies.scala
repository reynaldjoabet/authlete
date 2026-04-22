import sbt.*

object Dependencies {

  private object Version {

    // --- ZIO ecosystem ---
    val zio        = "2.1.25"
    val zioJson    = "0.9.1"
    val zioHttp    = "3.10.1"
    val zioLogging = "2.5.3"
    val zioConfig  = "4.0.7"
    val zioSchema  = "1.8.0"
    val zioKafka   = "3.3.0"

    // --- HTTP / API ---
    val http4s = "0.23.34"
    val sttp4  = "4.0.22"
    val tapir  = "1.13.15"

    // --- JSON / Serialization ---
    val jsoniter = "2.38.9"
    val circe    = "0.14.15"

    // --- Typelevel / FP ---
    val catsEffect = "3.7.0"
    val fs2        = "3.13.0"
    val fs2Kafka   = "4.0.0-RC2"
    val chimney    = "1.9.0"
    val iron       = "3.3.0"

    // --- Database / Persistence ---
    val doobie   = "1.0.0-RC5"
    val magnum   = "2.0.0-M3"
    val quill    = "4.8.6"
    val hikaricp = "7.0.2"
    val flyway   = "12.4.0"
    val skunk    = "1.0.0"
    val postgres = "42.7.10"

    // --- Auth / Security ---
    val jwtScala     = "11.0.4"
    val bouncycastle = "1.84"
    val password4j   = "1.8.4"
    val auth0        = "4.5.1"
    val nimbusJwt    = "10.9"
    val nimbusOidc   = "11.37"

    // --- Logging ---
    val scribe  = "3.19.0"
    val slf4j   = "2.0.17"
    val logback = "1.5.32"

    // --- Caching ---
    val caffeine = "3.2.3"

    // --- Config ---
    val pureconfig = "0.17.10"

    // --- Testing ---
    val munit      = "1.3.0"
    val scalacheck = "1.17.0"
    val scalaTest  = "3.2.19"

  }

  // ---------------------------------------------------------------------------
  // Helper constructors
  // ---------------------------------------------------------------------------
  private def http4s(artifact: String): ModuleID =
    "org.http4s" %% s"http4s-$artifact" % Version.http4s

  private def tapir(artifact: String): ModuleID =
    "com.softwaremill.sttp.tapir" %% s"tapir-$artifact" % Version.tapir

  private def sttp(artifact: String): ModuleID =
    "com.softwaremill.sttp.client4" %% artifact % Version.sttp4

  private def circe(artifact: String): ModuleID =
    "io.circe" %% s"circe-$artifact" % Version.circe

  private def doobie(artifact: String): ModuleID =
    "org.tpolecat" %% s"doobie-$artifact" % Version.doobie

  // ---------------------------------------------------------------------------
  // ZIO core
  // ---------------------------------------------------------------------------
  lazy val zio        = "dev.zio" %% "zio"          % Version.zio
  lazy val zioTest    = "dev.zio" %% "zio-test"     % Version.zio % Test
  lazy val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Version.zio % Test

  // ZIO JSON
  lazy val zioJson = "dev.zio" %% "zio-json" % Version.zioJson

  lazy val zioJsonGolden =
    "dev.zio" %% "zio-json-golden" % Version.zioJson % Test

  // ZIO HTTP
  lazy val zioHttp = "dev.zio" %% "zio-http" % Version.zioHttp

  // ZIO Logging
  lazy val zioLogging = "dev.zio" %% "zio-logging" % Version.zioLogging

  lazy val zioLoggingSlf4j =
    "dev.zio" %% "zio-logging-slf4j" % Version.zioLogging

  // ZIO Config
  lazy val zioConfig = "dev.zio" %% "zio-config" % Version.zioConfig

  lazy val zioConfigMagnolia =
    "dev.zio" %% "zio-config-magnolia" % Version.zioConfig

  lazy val zioConfigTypesafe =
    "dev.zio" %% "zio-config-typesafe" % Version.zioConfig

  // ZIO Schema
  lazy val zioSchema     = "dev.zio" %% "zio-schema"      % Version.zioSchema
  lazy val zioSchemaJson = "dev.zio" %% "zio-schema-json" % Version.zioSchema

  lazy val zioSchemaDerivation =
    "dev.zio" %% "zio-schema-derivation" % Version.zioSchema

  lazy val zioSchemaProtobuf =
    "dev.zio" %% "zio-schema-protobuf" % Version.zioSchema

  // ZIO Kafka
  lazy val zioKafka = "dev.zio" %% "zio-kafka" % Version.zioKafka

  // ---------------------------------------------------------------------------
  // HTTP / API clients
  // ---------------------------------------------------------------------------
  lazy val `http4s-dsl` = http4s("dsl")
  lazy val emberServer  = http4s("ember-server")
  lazy val emberClient  = http4s("ember-client")
  lazy val http4sCirce  = http4s("circe")

  // STTP
  lazy val sttpCore              = sttp("core")
  lazy val sttpJsoniter          = sttp("jsoniter")
  lazy val sttpFs2               = sttp("fs2")
  lazy val sttpCats              = sttp("cats")
  lazy val sttpCirce             = sttp("circe")
  lazy val sttpSlf4j             = sttp("slf4j-backend")
  lazy val sttpOkHttpBackend     = sttp("okhttp-backend")
  lazy val sttpPrometheusBackend = sttp("prometheus-backend")
  lazy val sttpScribeBackend     = sttp("scribe-backend")
  lazy val clientBackendFs2      = sttp("async-http-client-backend-fs2")
  lazy val http4sBackend         = sttp("http4s-backend")
  lazy val zioSttp               = sttp("zio")

  // Tapir
  lazy val tapirCore            = tapir("core")
  lazy val tapirHttp4sServer    = tapir("http4s-server")
  lazy val tapirJsoniterScala   = tapir("jsoniter-scala")
  lazy val tapirOpenAPIDocs     = tapir("openapi-docs")
  lazy val tapirSwaggerUIBundle = tapir("swagger-ui-bundle")

  // ---------------------------------------------------------------------------
  // JSON / Serialization
  // ---------------------------------------------------------------------------
  lazy val jsoniter =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % Version.jsoniter

  lazy val jsoniterMacros =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Version
      .jsoniter                              % "provided"

  lazy val jsoniterCirce =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe" % Version.jsoniter

  lazy val circeCore    = circe("core")
  lazy val circeGeneric = circe("generic")
  lazy val circeParser  = circe("parser")

  // ---------------------------------------------------------------------------
  // Data Transformation
  // ---------------------------------------------------------------------------
  lazy val chimney      = "io.scalaland"       %% "chimney"       % Version.chimney
  lazy val iron         = "io.github.iltotore" %% "iron"          % Version.iron
  lazy val ironZioJson  = "io.github.iltotore" %% "iron-zio-json" % Version.iron
  lazy val ironJsoniter = "io.github.iltotore" %% "iron-jsoniter" % Version.iron
  lazy val ironChimney  = "io.github.iltotore" %% "iron-chimney"  % Version.iron
  lazy val ironDoobie   = "io.github.iltotore" %% "iron-doobie"   % Version.iron
  lazy val ironSkunk    = "io.github.iltotore" %% "iron-skunk"    % Version.iron

  lazy val ironPureconfig =
    "io.github.iltotore" %% "iron-pureconfig" % Version.iron

  // ---------------------------------------------------------------------------
  // Typelevel / FP
  // ---------------------------------------------------------------------------
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % Version.catsEffect
  lazy val fs2        = "co.fs2"        %% "fs2-core"    % Version.fs2
  lazy val fs2Kafka   = "org.typelevel" %% "fs2-kafka"   % Version.fs2Kafka

  // ---------------------------------------------------------------------------
  // Database / Persistence
  // ---------------------------------------------------------------------------
  lazy val magnum    = "com.augustnagro" %% "magnum"         % Version.magnum
  lazy val quill     = "io.getquill"     %% "quill-jdbc-zio" % Version.quill
  lazy val hikaricp  = "com.zaxxer"       % "HikariCP"       % Version.hikaricp
  lazy val flyway    = "org.flywaydb"     % "flyway-core"    % Version.flyway
  lazy val skunkCore = "org.tpolecat"    %% "skunk-core"     % Version.skunk
  lazy val postgres  = "org.postgresql"   % "postgresql"     % Version.postgres

  // ---------------------------------------------------------------------------
  // Caching
  // ---------------------------------------------------------------------------
  lazy val caffeine =
    "com.github.ben-manes.caffeine" % "caffeine" % Version.caffeine

  // ---------------------------------------------------------------------------
  // Auth / Security
  // ---------------------------------------------------------------------------
  lazy val jwtCirce = "com.github.jwt-scala" %% "jwt-circe" % Version.jwtScala

  lazy val jwtZioJson =
    "com.github.jwt-scala" %% "jwt-zio-json" % Version.jwtScala

  lazy val auth0      = "com.auth0"      % "java-jwt"   % Version.auth0
  lazy val password4j = "com.password4j" % "password4j" % Version.password4j

  lazy val bouncycastle =
    "org.bouncycastle" % "bcpkix-jdk18on" % Version.bouncycastle

  lazy val bouncycastleProvider =
    "org.bouncycastle" % "bcprov-jdk18on" % Version.bouncycastle

  lazy val nimbusdsJoseJwt =
    "com.nimbusds" % "nimbus-jose-jwt" % Version.nimbusJwt

  lazy val nimbusdsOauth2OidcSdk =
    "com.nimbusds" % "oauth2-oidc-sdk" % Version.nimbusOidc

  // ---------------------------------------------------------------------------
  // Logging
  // ---------------------------------------------------------------------------
  lazy val scribe      = "com.outr" %% "scribe"       % Version.scribe
  lazy val scribeSlf4j = "com.outr" %% "scribe-slf4j" % Version.scribe
  lazy val scribeCats  = "com.outr" %% "scribe-cats"  % Version.scribe
  lazy val slf4j       = "org.slf4j" % "slf4j-api"    % Version.slf4j

  lazy val logback =
    "ch.qos.logback" % "logback-classic" % Version.logback

  // ---------------------------------------------------------------------------
  // Config
  // ---------------------------------------------------------------------------
  lazy val pureconfig =
    "com.github.pureconfig" %% "pureconfig-core" % Version.pureconfig

  lazy val pureconfigGeneric =
    "com.github.pureconfig" %% "pureconfig-generic-scala3" % Version.pureconfig

  // ---------------------------------------------------------------------------
  // Testing
  // ---------------------------------------------------------------------------
  lazy val munit     = "org.scalameta" %% "munit"     % Version.munit
  lazy val scalaTest = "org.scalatest" %% "scalatest" % Version.scalaTest
  // ---------------------------------------------------------------------------
  // Tasks
  // ---------------------------------------------------------------------------
  lazy val generate = taskKey[Unit]("generate code from APIs")

}

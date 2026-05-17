import sbt.*

object Dependencies {

  private object Version {

    // --- ZIO ecosystem ---
    val zio = "2.1.26"
    val zioJson = "0.9.2"
    val zioHttp = "3.11.1"
    val zioLogging = "2.5.3"
    val zioConfig = "4.0.7"
    val zioSchema = "1.8.5"
    val zioKafka = "3.4.0"

    // --- HTTP ---
    val http4s = "0.23.34"
    val sttp4 = "4.0.23"
    val tapir = "1.13.18"

    // --- JSON ---
    val jsoniter = "2.38.12"
    val circe = "0.14.15"

    // --- FP ---
    val catsEffect = "3.7.0"
    val fs2 = "3.13.0"
    val fs2Kafka = "4.0.0"
    val chimney = "1.10.0"
    val iron = "3.3.1"
    val hedgehog = "0.13.0"
    val scalacheck = "1.19.0"
    val munit = "1.3.0"
    val munitCatsEffect = "2.2.0"

    // --- DB ---
    val quill = "4.8.6"
    val magnum = "2.0.0-M3"
    val skunk = "1.1.0-RC1"
    val hikaricp = "7.0.2"
    val flyway = "12.6.1"
    val postgres = "42.7.11"

    // --- Security ---
    val jwtScala = "11.0.4"
    val bouncycastle = "1.84"
    val password4j = "1.8.4"
    val auth0 = "4.5.2"
    val nimbusJoseJwt = "10.9"
    val nimbusOauth2Oidc = "11.37.1"
    val vault = "5.1.0"

    // --- Logging ---
    val scribe = "3.19.0"
    val slf4j = "2.0.18"
    val logback = "1.5.32"

    // --- Cache ---
    val caffeine = "3.2.4"

    // --- Observability ---
    val datadog = "2.35.0"
    val kamon = "2.7.7"
    val otel4s = "1.0.0"

    // --- Config ---
    val pureconfig = "0.17.10"

    // --- Cloud ---
    val awsV2 = "2.26.15"
    val azureIdentity = "1.17.0"
    val azureKv = "4.9.4"
  }

  // Helpers
  private def http4s(a: String) =
    "org.http4s" %% s"http4s-$a" % Version.http4s

  private def tapir(a: String) =
    "com.softwaremill.sttp.tapir" %% s"tapir-$a" % Version.tapir

  private def sttp(a: String) =
    "com.softwaremill.sttp.client4" %% a % Version.sttp4

  private def circe(a: String) =
    "io.circe" %% s"circe-$a" % Version.circe

  // ZIO
  lazy val zio = "dev.zio" %% "zio" % Version.zio
  lazy val zioTest = "dev.zio" %% "zio-test" % Version.zio % Test
  lazy val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Version.zio % Test

  lazy val zioJson = "dev.zio" %% "zio-json" % Version.zioJson
  lazy val zioJsonGolden =
    "dev.zio" %% "zio-json-golden" % Version.zioJson % Test

  lazy val zioHttp = "dev.zio" %% "zio-http" % Version.zioHttp

  lazy val zioLogging = "dev.zio" %% "zio-logging" % Version.zioLogging
  lazy val zioLoggingSlf4j =
    "dev.zio" %% "zio-logging-slf4j" % Version.zioLogging

  lazy val zioConfig = "dev.zio" %% "zio-config" % Version.zioConfig
  lazy val zioConfigMagnolia =
    "dev.zio" %% "zio-config-magnolia" % Version.zioConfig
  lazy val zioConfigTypesafe =
    "dev.zio" %% "zio-config-typesafe" % Version.zioConfig

  lazy val zioSchema = "dev.zio" %% "zio-schema" % Version.zioSchema
  lazy val zioSchemaJson = "dev.zio" %% "zio-schema-json" % Version.zioSchema
  lazy val zioSchemaDerivation =
    "dev.zio" %% "zio-schema-derivation" % Version.zioSchema
  lazy val zioSchemaProtobuf =
    "dev.zio" %% "zio-schema-protobuf" % Version.zioSchema

  lazy val zioKafka = "dev.zio" %% "zio-kafka" % Version.zioKafka

  // HTTP
  lazy val http4sDsl = http4s("dsl")
  lazy val emberServer = http4s("ember-server")
  lazy val emberClient = http4s("ember-client")
  lazy val http4sCirce = http4s("circe")

  lazy val sttpCore = sttp("core")
  lazy val sttpJsoniter = sttp("jsoniter")
  lazy val sttpFs2 = sttp("fs2")
  lazy val sttpCats = sttp("cats")
  lazy val sttpCirce = sttp("circe")
  lazy val sttpSlf4j = sttp("slf4j-backend")
  lazy val sttpOkHttpBackend = sttp("okhttp-backend")
  lazy val sttpPrometheusBackend = sttp("prometheus-backend")
  lazy val sttpScribeBackend = sttp("scribe-backend")
  lazy val clientBackendFs2 = sttp("async-http-client-backend-fs2")
  lazy val http4sBackend = sttp("http4s-backend")
  lazy val zioSttp = sttp("zio")

  lazy val tapirCore = tapir("core")
  lazy val tapirHttp4s = tapir("http4s-server")
  lazy val tapirHttp4sServer = tapir("http4s-server")
  lazy val tapirJsoniter = tapir("jsoniter-scala")
  lazy val tapirJsoniterScala = tapir("jsoniter-scala")
  lazy val tapirOpenAPIDocs = tapir("openapi-docs")
  lazy val tapirSwagger = tapir("swagger-ui-bundle")

  // JSON
  lazy val jsoniter =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % Version.jsoniter
  lazy val jsoniterMacros =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Version.jsoniter % Provided
  lazy val jsoniterCirce =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe" % Version.jsoniter

  lazy val circeCore = circe("core")
  lazy val circeGeneric = circe("generic")
  lazy val circeParser = circe("parser")

  // Data
  lazy val chimney = "io.scalaland" %% "chimney" % Version.chimney
  lazy val iron = "io.github.iltotore" %% "iron" % Version.iron
  lazy val ironZioJson = "io.github.iltotore" %% "iron-zio-json" % Version.iron
  lazy val ironJsoniter = "io.github.iltotore" %% "iron-jsoniter" % Version.iron
  lazy val ironChimney = "io.github.iltotore" %% "iron-chimney" % Version.iron
  lazy val ironDoobie = "io.github.iltotore" %% "iron-doobie" % Version.iron
  lazy val ironSkunk = "io.github.iltotore" %% "iron-skunk" % Version.iron
  lazy val ironPureconfig =
    "io.github.iltotore" %% "iron-pureconfig" % Version.iron

  // Typelevel
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % Version.catsEffect
  lazy val fs2 = "co.fs2" %% "fs2-core" % Version.fs2
  lazy val fs2Kafka = "org.typelevel" %% "fs2-kafka" % Version.fs2Kafka

  // DB
  lazy val quill = "io.getquill" %% "quill-jdbc-zio" % Version.quill
  lazy val magnum = "com.augustnagro" %% "magnum" % Version.magnum
  lazy val skunkCore = "org.tpolecat" %% "skunk-core" % Version.skunk

  lazy val postgres = "org.postgresql" % "postgresql" % Version.postgres
  lazy val hikaricp = "com.zaxxer" % "HikariCP" % Version.hikaricp
  lazy val flyway = "org.flywaydb" % "flyway-core" % Version.flyway

  // AWS v2
  lazy val ec2 = "software.amazon.awssdk" % "ec2" % Version.awsV2
  lazy val iam = "software.amazon.awssdk" % "iam" % Version.awsV2
  lazy val rds = "software.amazon.awssdk" % "rds" % Version.awsV2
  lazy val Kms = "software.amazon.awssdk" % "kms" % Version.awsV2
  lazy val secretsManager =
    "software.amazon.awssdk" % "secretsmanager" % Version.awsV2
  lazy val sts = "software.amazon.awssdk" % "sts" % Version.awsV2

  // Security
  lazy val jwtZioJson =
    "com.github.jwt-scala" %% "jwt-zio-json" % Version.jwtScala
  lazy val jwtCirce="com.github.jwt-scala" %% "jwt-circe" %  Version.jwtScala
  lazy val auth0 = "com.auth0" % "java-jwt" % Version.auth0
  lazy val password4j =
    "com.password4j" % "password4j" % Version.password4j

  lazy val nimbusJoseJwt =
    "com.nimbusds" % "nimbus-jose-jwt" % Version.nimbusJoseJwt
  lazy val nimbusOauth2Oidc =
    "com.nimbusds" % "oauth2-oidc-sdk" % Version.nimbusOauth2Oidc

  lazy val bouncycastle =
    "org.bouncycastle" % "bcpkix-jdk18on" % Version.bouncycastle
  lazy val bouncycastleProvider =
    "org.bouncycastle" % "bcprov-jdk18on" % Version.bouncycastle

  lazy val pemKeystore =
    "de.dentrassi.crypto" % "pem-keystore" % "3.0.0"

  lazy val vault =
    "com.bettercloud" % "vault-java-driver" % Version.vault

  // Azure
  lazy val azureIdentity =
    "com.azure" % "azure-identity" % Version.azureIdentity
  lazy val azureKeyVaultKeys =
    "com.azure" % "azure-security-keyvault-keys" % Version.azureKv
  lazy val azureKeyVaultSecrets =
    "com.azure" % "azure-security-keyvault-secrets" % Version.azureKv

  // Observability
  lazy val datadog =
    "com.datadoghq" % "datadog-api-client" % Version.datadog classifier "shaded-jar"

  lazy val kamon =
    "io.kamon" %% "kamon-bundle" % Version.kamon
  lazy val kamonPrometheus =
    "io.kamon" %% "kamon-prometheus" % Version.kamon

  lazy val otel4sCore =
    "org.typelevel" %% "otel4s-core" % Version.otel4s
  lazy val otel4sOtlp =
    "org.typelevel" %% "otel4s-otlp" % Version.otel4s
  lazy val otelJava = "org.typelevel" %% "otel4s-otelJava" % Version.otel4s

  // Logging
  lazy val scribe = "com.outr" %% "scribe" % Version.scribe
  lazy val scribeSlf4j =
    "com.outr" %% "scribe-slf4j" % Version.scribe
  lazy val scribeCats =
    "com.outr" %% "scribe-cats" % Version.scribe

  lazy val slf4j = "org.slf4j" % "slf4j-api" % Version.slf4j
  lazy val logback =
    "ch.qos.logback" % "logback-classic" % Version.logback

  // Config
  lazy val pureconfig =
    "com.github.pureconfig" %% "pureconfig-core" % Version.pureconfig
  lazy val pureconfigGeneric =
    "com.github.pureconfig" %% "pureconfig-generic-scala3" % Version.pureconfig

  // Cache
  lazy val caffeine =
    "com.github.ben-manes.caffeine" % "caffeine" % Version.caffeine

  // Testing
  lazy val hedgehog =
    "qa.hedgehog" %% "hedgehog-core" % Version.hedgehog % Test
  lazy val hedgehogSbt =
    "qa.hedgehog" %% "hedgehog-sbt" % Version.hedgehog % Test

  lazy val scalacheck =
    "org.scalacheck" %% "scalacheck" % Version.scalacheck % Test

  lazy val munit =
    "org.scalameta" %% "munit" % Version.munit % Test

  lazy val munitCatsEffect =
    "org.typelevel" %% "munit-cats-effect" % Version.munitCatsEffect % Test

  // Tasks
  lazy val generate = taskKey[Unit]("generate code from APIs")
}
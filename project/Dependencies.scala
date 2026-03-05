import sbt._

object Dependencies {

  object Version {

    val catsEffect = "3.6.3"
    val http4s = "0.23.33"
    val tapir = "1.10.6"
    val jsoniter = "2.38.8"
    val sttp4 = "4.0.15"
    val doobie = "1.0.0-RC5"
    val flyway = "10.18.2"
    val pureconfig = "0.17.9"
    val logback = "1.5.22"
    val slf4j = "2.0.17"
    val scalacheck = "1.17.0"
    val fs2 = "3.12.2"
    val scribe = "3.17.0"
    val chimney = "1.8.2"
    val munit = "1.2.2"
    val caffeine = "3.2.3"
    val circe = "0.14.14"
    val zio = "2.1.24"
    val zioJson = "0.9.0"
    val zioHttp = "3.8.1"
    val zioLogging = "2.5.3"
    val zioConfig = "4.0.6"
    val zioSchema = "1.8.0"
    val zioKafka = "3.2.0"

    val bouncycastle = "1.83"

  }

  def http4s(artifact: String): ModuleID =
    "org.http4s" %% s"http4s-$artifact" % Version.http4s

  def tapir(artifact: String): ModuleID =
    "com.softwaremill.sttp.tapir" %% s"tapir-$artifact" % Version.tapir

  def sttp(artifact: String): ModuleID =
    "com.softwaremill.sttp.client4" %% artifact % Version.sttp4

  def circe(artifact: String): ModuleID =
    "io.circe" %% s"circe-$artifact" % Version.circe

  lazy val chimney = "io.scalaland" %% "chimney" % Version.chimney
  lazy val `http4s-dsl` = http4s("dsl")
  lazy val emberServer = http4s("ember-server")
  lazy val emberClient = http4s("ember-client")
  lazy val http4sCirce = http4s("circe")
  lazy val sttpCore = sttp("core")
  lazy val sttpFs2 = sttp("fs2")
  lazy val sttpCats = sttp("cats")
  lazy val sttpCirce = sttp("circe")
  lazy val sttpJsoniter = sttp("jsoniter")
  lazy val zioSttp = sttp("zio")
  lazy val sttpSlf4j = sttp("slf4j-backend")
// https://mvnrepository.com/artifact/com.softwaremill.sttp.client4/async-http-client-backend-fs2
  lazy val clientBackendFs2 = sttp("async-http-client-backend-fs2")
  lazy val http4sBackend = sttp("http4s-backend")

  lazy val sttpOkHttpBackend = sttp("okhttp-backend")
  lazy val sttpPrometheusBackend = sttp("prometheus-backend")
  lazy val sttpScribeBackend = sttp("scribe-backend")
  lazy val generate = taskKey[Unit]("generate code from APIs")

  lazy val munit = "org.scalameta" %% "munit" % Version.munit

  lazy val scribe = "com.outr" %% "scribe" % "3.16.1"
  lazy val scribeSlf4j = "com.outr" %% "scribe-slf4j" % "3.16.1"
  lazy val scribeCats = "com.outr" %% "scribe-cats" % "3.16.1"

  lazy val jsoniter =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % Version.jsoniter

  lazy val jsoniterMacros =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Version.jsoniter % "provided"

  lazy val jsoniterCirce =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe" % Version.jsoniter

  lazy val catsEffect = "org.typelevel" %% "cats-effect" % Version.catsEffect

  lazy val pureconfig =
    "com.github.pureconfig" %% "pureconfig-core" % Version.pureconfig

  lazy val slf4j = "org.slf4j" % "slf4j-api" % Version.slf4j

  lazy val logback =
    "ch.qos.logback" % "logback-classic" % Version.logback % Runtime

  lazy val fs2 = "co.fs2" %% "fs2-core" % Version.fs2

  lazy val nimbusdsJoseJwt =
    "com.nimbusds" % "nimbus-jose-jwt" % "10.7"

  lazy val nimbusdsOauth2OidcSdk =
    "com.nimbusds" % "oauth2-oidc-sdk" % "11.33"

  lazy val jwtCirce = "com.github.jwt-scala" %% "jwt-circe" % "11.0.3"

  lazy val caffeine =
    "com.github.ben-manes.caffeine" % "caffeine" % Version.caffeine

  lazy val circeCore = circe("core")
  lazy val circeGeneric = circe("generic")
  lazy val circeParser = circe("parser")

  lazy val zio = "dev.zio" %% "zio" % Version.zio

  lazy val zioTest = "dev.zio" %% "zio-test" % Version.zio % Test

  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Version.zio % Test

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

  lazy val bouncycastle =
    "org.bouncycastle" % "bcpkix-jdk18on" % Version.bouncycastle // contains the provider
  lazy val bouncycastleProvider =
    "org.bouncycastle" % "bcprov-jdk18on" % Version.bouncycastle // The Bouncy Castle Crypto package is a Java implementation of cryptographic algorithms.
}
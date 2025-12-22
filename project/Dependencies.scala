import sbt._

object Dependencies {
  object Version {
    val catsEffect = "3.6.3"
    val http4s = "0.23.33"
    val tapir = "1.10.6"
    val jsoniter = "2.38.4"
    val sttp4 = "4.0.13"
    val doobie = "1.0.0-RC5"
    val flyway = "10.18.2"
    val pureconfig = "0.17.9"
    val logback = "1.5.22"
    val slf4j = "2.0.17"
    val scalacheck = "1.17.0"
    val fs2 = "3.12.2"
    val scribe = "3.17.0"
    val chimney = "1.8.2"
    val munit = "1.2.1"
  }
  def http4s(artifact: String): ModuleID =
    "org.http4s" %% s"http4s-$artifact" % Version.http4s

  def tapir(artifact: String): ModuleID =
    "com.softwaremill.sttp.tapir" %% s"tapir-$artifact" % Version.tapir

  def sttp(artifact: String): ModuleID =
    "com.softwaremill.sttp.client4" %% artifact % Version.sttp4
  lazy val chimney = "io.scalaland" %% "chimney" % Version.chimney
  lazy val `http4s-dsl` = http4s("dsl")
  lazy val emberServer = http4s("ember-server")
  lazy val emberClient = http4s("ember-client")

  lazy val sttpCore = sttp("core")
  lazy val sttpFs2 = sttp("fs2")
  lazy val sttpCats = sttp("cats")
  lazy val sttpCirce = sttp("circe")
  lazy val sttpJsoniter = sttp("jsoniter")
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
}

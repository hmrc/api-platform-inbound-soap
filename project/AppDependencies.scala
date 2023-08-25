import play.core.PlayVersion
import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val bootstrapVersion = "7.20.0"

  val jacksonVersion         = "2.13.4"
  val jacksonDatabindVersion = "2.13.4.2"

  val jacksonOverrides = Seq(
    "com.fasterxml.jackson.core"     % "jackson-core",
    "com.fasterxml.jackson.core"     % "jackson-annotations",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"
  ).map(_ % jacksonVersion)

  val jacksonDatabindOverrides = Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion
  )

  val akkaSerializationJacksonOverrides = Seq(
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor",
    "com.fasterxml.jackson.module"     % "jackson-module-parameter-names",
    "com.fasterxml.jackson.module"     %% "jackson-module-scala"
  ).map(_ % jacksonVersion)


  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % bootstrapVersion,
    "com.auth0"               %  "java-jwt"                   % "4.4.0"
    excludeAll(
      ExclusionRule("com.fasterxml.jackson.core", "jackson-databind")
    )
  ) ++ jacksonDatabindOverrides ++ jacksonOverrides ++ akkaSerializationJacksonOverrides

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion    ,
    "org.scalatest"           %% "scalatest"                  % "3.2.12"            ,
    "org.mockito"             %% "mockito-scala-scalatest"    % "1.17.7"            ,
    "com.typesafe.play"       %% "play-test"                  % current             ,
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.64.0"            ,
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "5.1.0"             ,
    "org.xmlunit"             %  "xmlunit-core"               % "2.9.0"
  ).map(_ % "test, it")
}

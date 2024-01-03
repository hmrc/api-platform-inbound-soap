import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

lazy val appName = "api-platform-inbound-soap"

scalaVersion := "2.13.12"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion        := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)

commands ++= Seq(
  Command.command("run-all-tests") { state => "test" :: "IntegrationTest/test" :: state },

  Command.command("clean-and-test") { state => "clean" :: "compile" :: "run-all-tests" :: state },

  // Coverage does not need compile !
  Command.command("pre-commit") { state => "clean" :: "scalafmtAll" :: "scalafixAll" :: "coverage" :: "run-all-tests" :: "coverageReport" :: "coverageOff" :: state }
)
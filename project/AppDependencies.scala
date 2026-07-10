import sbt.*

object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test

  private val bootstrapVersion = "10.7.1"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "com.auth0"               %  "java-jwt"                   % "4.4.0",
    "org.typelevel"           %% "cats-core"                  % "2.10.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion,
    "org.mockito"             %% "mockito-scala-scalatest"    % "2.2.1",
    "com.sun.activation"      % "javax.activation"            % "1.2.0",
    "org.xmlunit"             %  "xmlunit-core"               % "2.9.0",
    "jakarta.xml.bind"        % "jakarta.xml.bind-api"        % "2.3.2",
    "org.glassfish.jaxb"      % "jaxb-runtime"                % "2.3.2"
  ).map(_ % "test")
}

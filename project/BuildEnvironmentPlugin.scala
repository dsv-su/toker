import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

/** sets the build environment */
object BuildEnvironmentPlugin extends AutoPlugin {

  // make sure it triggers automatically
  override def trigger = AllRequirements
  override def requires = JvmPlugin

  object autoImport {
    sealed trait BuildEnvironment
    case object Production extends BuildEnvironment
    case object Staging extends BuildEnvironment
    case object Development extends BuildEnvironment

    val buildEnv = settingKey[BuildEnvironment]("the current build environment")
  }
  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    buildEnv := {
      sys.props.get("env")
        .orElse(sys.env.get("BUILD_ENV"))
        .flatMap {
          case "prod" => Some(Production)
          case "stage" => Some(Staging)
          case "dev" => Some(Development)
          case _ => None
        }
        .getOrElse(Production)
    },
    // give feed back
    onLoadMessage := {
      // depend on the old message as well
      val defaultMessage = onLoadMessage.value
      val env = buildEnv.value
      s"""|$defaultMessage
          |Running in build environment: $env""".stripMargin
    }
  )

}
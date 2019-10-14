package services

import play.api.{Logger, Plugin, Application}
import play.libs.Akka
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Plugin for TempFiles.
 */
class TempFilesPlugin(application: Application) extends Plugin {

  val files: FileService =  DI.injector.getInstance(classOf[FileService])

  override def onStart() {
    Logger.debug("Starting up Temp Files Plugin")
    //Delete garbage files (ie past intermediate extractor results files) from DB
    var timeInterval = play.Play.application().configuration().getInt("intermediateCleanup.checkEvery")
    Akka.system().scheduler.schedule(0.hours, timeInterval.intValue().hours) {
      files.removeOldIntermediates(None, None)
    }
  }

  override def onStop() {
    Logger.debug("Shutting down Temp Files Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("filedumpservice").filter(_ == "no").isDefined
  }

}

package services

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.{Application, Logger}
import play.libs.Akka

import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

trait TempFilesService

/**
 * Plugin for TempFiles.
 */
class TempFilesServiceImpl @Inject() (lifecycle: ApplicationLifecycle, actorSystem: ActorSystem) extends TempFilesService {

  val files: FileService =  DI.injector.instanceOf[FileService]

  Logger.debug("Starting up Temp Files Plugin")
  //Delete garbage files (ie past intermediate extractor results files) from DB
  var timeInterval = configuration.get[Int]("intermediateCleanup.checkEvery")
  actorSystem.scheduler.schedule(0.hours, timeInterval.intValue().hours) {
    files.removeOldIntermediates()
  }

  lifecycle.addStopHook { () =>
    Logger.debug("Shutting down Temp Files Plugin")
    Future.successful(())
  }

  lazy val enabled = {
    import play.api.Play.current
    !current.configuration.getString("filedumpservice").filter(_ == "no").isDefined
  }
}

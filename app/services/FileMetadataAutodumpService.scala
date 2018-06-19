package services

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.{Application, Logger}
import play.libs.Akka

import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

class FileMetadataAutodumpService

/**
 * File metadata automatic dump service.
 *
 */
class FileMetadataAutodumpServiceImpl @Inject() (lifecycle: ApplicationLifecycle, actorSystem: ActorSystem)
  extends FileMetadataAutodumpService {

  val files: FileService = DI.injector.instanceOf[FileService]

  Logger.debug("Starting file metadata autodumper Plugin")
  //Dump metadata of all files periodically
  val timeInterval = configuration.get[Int]("filemetadatadump.dumpEvery")
	actorSystem.scheduler.schedule(0.days, timeInterval.intValue().days){
	      dumpAllFileMetadata
	}

  lifecycle.addStopHook { () =>
    Logger.debug("Shutting down file metadata autodumper Plugin")
    Future.successful(())
  }

  lazy val enabled = {
    import play.api.Play.current
    !current.configuration.getString("filemetadatadumpservice").filter(_ == "disabled").isDefined
  }
  
  def dumpAllFileMetadata() = {
    val unsuccessfulDumps = files.dumpAllFileMetadata
    if(unsuccessfulDumps.size == 0)
      Logger.debug("Dumping and staging of files metadata was successful for all files.")
    else{
      var unsuccessfulMessage = "Dumping of files metadata was successful for all files except file(s) with id(s) "
      for(badFile <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badFile + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Logger.debug(unsuccessfulMessage)
    } 
    
  }
  
}
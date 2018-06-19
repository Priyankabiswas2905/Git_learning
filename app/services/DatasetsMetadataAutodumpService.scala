package services

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.{Application, Logger}
import play.libs.Akka

import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

trait DatasetsMetadataAutodumpService

/**
 * Dataset metadata automatic dump service.
 *
 */
class DatasetsMetadataAutodumpServiceImpl @Inject() (lifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem) extends DatasetsMetadataAutodumpService {

  val datasets: DatasetService = DI.injector.instanceOf[DatasetService]

  Logger.debug("Starting dataset metadata autodumper Plugin")
  //Dump metadata of all datasets periodically
  val timeInterval = configuration.get[Int]("datasetmetadatadump.dumpEvery")
  actorSystem.scheduler.schedule(0.days, timeInterval.intValue().days){
    dumpAllDatasetMetadata
  }

  lifecycle.addStopHook { () =>
    Logger.debug("Shutting down dataset metadata autodumper Plugin")
    Future.successful(())
  }

  lazy val enabled = {
    import play.api.Play.current
    !current.configuration.getString("datasetmetadatadumpservice").filter(_ == "disabled").isDefined
  }
  
  def dumpAllDatasetMetadata() = {
    val unsuccessfulDumps = datasets.dumpAllDatasetMetadata
    if(unsuccessfulDumps.size == 0)
      Logger.debug("Dumping of datasets metadata was successful for all datasets.")
    else{
      var unsuccessfulMessage = "Dumping of datasets metadata was successful for all datasets except dataset(s) with id(s) "
      for(badDataset <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Logger.debug(unsuccessfulMessage)
    } 
    
  }
  
}
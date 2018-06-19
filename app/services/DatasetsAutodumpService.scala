package services

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.{Application, Configuration, Logger, Play}
import play.libs.Akka

import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

trait DatasetsAutodumpService

/**
 * Dataset file groupings automatic dump service.
 */
class DatasetsAutodumpServiceImpl @Inject() (lifecycle: ApplicationLifecycle, actorSystem: ActorSystem,
  configuration: Configuration) extends DatasetsAutodumpService {

  val datasets: DatasetService = DI.injector.instanceOf[DatasetService]

  Logger.debug("Starting dataset file groupings autodumper Plugin")
  //Dump dataset file groupings periodically
  val timeInterval = configuration.get[Int]("datasetdump.dumpEvery")
    actorSystem.scheduler.schedule(0.days, timeInterval.intValue().days){
      dumpDatasetGroupings
	}

  lifecycle.addStopHook { () =>
    Logger.debug("Shutting down dataset file groupings autodumper Plugin")
    Future.successful(())
  }

  lazy val enabled = {
    !(configuration.get[String]("datasetsdumpservice") == "disabled")
  }
  
  def dumpDatasetGroupings() = {
    
    val unsuccessfulDumps = datasets.dumpAllDatasetGroupings
    if(unsuccessfulDumps.size == 0)
      Logger.debug("Dumping of dataset file groupings was successful for all datasets.")
    else{
      var unsuccessfulMessage = "Dumping of dataset file groupings was successful for all datasets except dataset(s) with id(s) "
      for(badDataset <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Logger.debug(unsuccessfulMessage)
    }      
  }
  
}
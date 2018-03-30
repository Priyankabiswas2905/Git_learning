package services

import javax.inject.Inject
import play.api.{Application, Logger}
import play.libs.Akka

import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

trait RDFExporterService

/**
 * Plugin for RDF Exporter
 */
class RDFExporterPlugin @Inject() (lifecycle: ApplicationLifecycle) extends RDFExporterService {

  val files: FileService =  DI.injector.instanceOf[FileService]

  Logger.debug("Starting up RDF Exporter Plugin")
  //Clean temporary RDF files if RDF exporter is activated
  if(current.configuration.getBoolean("isRDFExportEnabled").getOrElse(false)){
    val timeInterval = play.Play.application().configuration().getInt("rdfTempCleanup.checkEvery")
    Akka.system().scheduler.schedule(0.minutes, timeInterval.intValue().minutes){
      files.removeTemporaries()
    }
  }

  lifecycle.addStopHook { () =>
    Logger.debug("Shutting down RDF Exporter Plugin")
    Future.successful(())
  }

  lazy val enabled = {
    import play.api.Play.current
    !current.configuration.getString("rdfexporter").filter(_ == "no").isDefined
  }
}

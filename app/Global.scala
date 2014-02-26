import com.mongodb.casbah.Imports._
import play.api.{ GlobalSettings, Application }
import play.api.Logger
import play.api.Play.current
import services._
import play.libs.Akka
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends GlobalSettings {

  override def onStart(app: Application) {
    // create mongo indexes if plugin is loaded
    current.plugin[MongoSalatPlugin].map { mongo =>
      mongo.sources.values.map { source =>
        Logger.debug("Ensuring indexes on " + source.uri)
        source.collection("datasets").ensureIndex(MongoDBObject("created" -> -1))
        source.collection("datasets").ensureIndex(MongoDBObject("tags" -> 1))
        source.collection("uploads.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
        source.collection("uploadquery.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
        source.collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
        source.collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "section_id" -> 1))
        source.collection("sections").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
      }
    }
        
    //Add permanent admins to app if not already included
    models.AppConfiguration.getDefault()
    for(initialAdmin <- play.Play.application().configuration().getString("initialAdmins").split(","))
    	models.AppConfiguration.addAdmin(initialAdmin)
    
    //Delete garbage files (ie past intermediate extractor results files) from DB
    var timeInterval = play.Play.application().configuration().getInt("intermediateCleanup.checkEvery")
    Akka.system().scheduler.schedule(0.hours, timeInterval.intValue().hours){
      models.FileDAO.removeOldIntermediates()
    }
  //Clean temporary RDF files if RDF exporter is activated
    if(current.plugin[RDFExportService].isDefined){
	    timeInterval = play.Play.application().configuration().getInt("rdfTempCleanup.checkEvery")
	    Akka.system().scheduler.schedule(0.minutes, timeInterval.intValue().minutes){
	      models.FileDAO.removeTemporaries()
	    }
    }
    
    //Dump metadata of all files periodically if file metadata autodumper is activated
    if(current.plugin[FileMetadataAutodumpService].isDefined){
	    timeInterval = play.Play.application().configuration().getInt("filemetadatadump.dumpEvery") 
	    Akka.system().scheduler.schedule(0.days, timeInterval.intValue().days){
	      current.plugin[FileMetadataAutodumpService].get.dumpAllFileMetadata
	    }
    }
    
    //Dump dataset file groupings periodically if dataset file groupings autodumper is activated
    if(current.plugin[DatasetsAutodumpService].isDefined){
	    timeInterval = play.Play.application().configuration().getInt("datasetdump.dumpEvery") 
	    Akka.system().scheduler.schedule(0.days, timeInterval.intValue().days){
	      current.plugin[DatasetsAutodumpService].get.dumpDatasetGroupings
	    }
    }
    
    //Dump metadata of all datasets periodically if dataset metadata autodumper is activated
    if(current.plugin[DatasetsMetadataAutodumpService].isDefined){
	    timeInterval = play.Play.application().configuration().getInt("datasetmetadatadump.dumpEvery") 
	    Akka.system().scheduler.schedule(0.days, timeInterval.intValue().days){
	      current.plugin[DatasetsMetadataAutodumpService].get.dumpAllDatasetMetadata
	    }
    }
    
  }

  override def onStop(app: Application) {
  }

  private lazy val injector = services.DI.injector

  /** Used for dynamic controller dispatcher **/
  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }

}

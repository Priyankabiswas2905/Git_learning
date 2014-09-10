import com.mongodb.casbah.Imports._
import play.api.{GlobalSettings, Application}
import play.api.Logger
import play.api.Play.current
import services.mongodb.MongoSalatPlugin
import services.mongodb.MongoDBAppConfigurationService
import services._
import play.libs.Akka
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import models.ExtractionInfoSetUp
import services.ExtractorService
import java.util.Date
import java.util.Calendar
import models._
import play.api.mvc.WithFilters
import play.filters.gzip.GzipFilter
import akka.actor.Cancellable

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends WithFilters(new GzipFilter(),CORSFilter()) with GlobalSettings  {
  
  var serverStartTime:Date=null
  var extractorTimer: Cancellable = null

  override def onStart(app: Application) {
    ServerStartTime.startTime = Calendar.getInstance().getTime()
    serverStartTime = ServerStartTime.startTime
    Logger.debug("\n----Server Start Time----" + serverStartTime + "\n \n")
    
    // create mongo indexes if plugin is loaded
    current.plugin[MongoSalatPlugin].map {
      mongo =>
        mongo.sources.values.map {
          source =>
            Logger.debug("Ensuring indexes on " + source.uri)
            source.collection("collections").ensureIndex(MongoDBObject("created" -> -1))
            
            source.collection("datasets").ensureIndex(MongoDBObject("created" -> -1))
            source.collection("datasets").ensureIndex(MongoDBObject("tags" -> 1))
            source.collection("datasets").ensureIndex(MongoDBObject("files._id" -> 1))
            
            source.collection("uploads.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
            source.collection("uploads.files").ensureIndex(MongoDBObject("tags" -> 1))
            source.collection("uploadquery.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
            
            source.collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
            source.collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "section_id" -> 1))
            
            source.collection("textures.files").ensureIndex(MongoDBObject("file_id" -> 1))
            source.collection("tiles.files").ensureIndex(MongoDBObject("preview_id" -> 1, "filename" -> 1,"level" -> 1))
            
            source.collection("sections").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
            
            source.collection("dtsrequests").ensureIndex(MongoDBObject("startTime" -> -1, "endTime" -> -1))
            source.collection("versus.descriptors").ensureIndex(MongoDBObject("preview_id" -> 1, "file_id" -> 1 )) 
            
            //Indexing for users access rights
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1))
            
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "collectionsViewOnly" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "collectionsViewModify" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "collectionsAdministrate" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "datasetsViewOnly" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "datasetsViewModify" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "datasetsAdministrate" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "filesViewOnly" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "filesViewModify" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("email" -> 1, "name" -> 1, "filesAdministrate" -> 1))
            
            source.collection("useraccessrights").ensureIndex(MongoDBObject("collectionsViewOnly" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("collectionsViewModify" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("collectionsAdministrate" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("datasetsViewOnly" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("datasetsViewModify" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("datasetsAdministrate" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("filesViewOnly" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("filesViewModify" -> 1))
            source.collection("useraccessrights").ensureIndex(MongoDBObject("filesAdministrate" -> 1))
        }
    }

    //Add permanent admins to app if not already included
    val appConfObj = new services.mongodb.MongoDBAppConfigurationService{}    
    appConfObj.getDefault()
    for(initialAdmin <- play.Play.application().configuration().getString("initialAdmins").split(","))
    	appConfObj.addAdmin(initialAdmin)
    
    extractorTimer = Akka.system().scheduler.schedule(0.minutes,5 minutes){
           ExtractionInfoSetUp.updateExtractorsInfo()
    }
    
    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    extractorTimer.cancel
    Logger.info("Application shutdown")
  }

  private lazy val injector = services.DI.injector

  /** Used for dynamic controller dispatcher **/
  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }
}

package controllers

import play.api.Routes
import play.api.mvc.Action
import play.api.mvc.Controller
import api.Sections
import api.WithPermission
import api.Permission
import models.FileDAO
import com.mongodb.casbah.commons.MongoDBObject

/**
 * Main application controller.
 * 
 * @author Luigi Marini
 */
object Application extends SecuredController {
  
  /**
   * Main page.
   */
  def index = SecuredAction() { request =>
  	implicit val user = request.user
  	val latestFiles = FileDAO.find(MongoDBObject()).sort(MongoDBObject("uploadDate" -> -1)).limit(5).toList
    Ok(views.html.index(latestFiles))
  }
  
  /**
   * Testing action.
   */
  def testJson = SecuredAction()  { implicit request =>
    Ok("{test:1}").as(JSON)
  }
  
    
  /**
   *  Javascript routing.
   */
  def javascriptRoutes = SecuredAction() { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        routes.javascript.Admin.test,
        routes.javascript.Admin.secureTest,
        routes.javascript.Admin.reindexFiles,
        routes.javascript.Admin.createIndex,
        routes.javascript.Admin.buildIndex,
        routes.javascript.Admin.deleteIndex,
        routes.javascript.Admin.deleteAllIndexes,
        routes.javascript.Admin.getIndexes,
        routes.javascript.Tags.search,
        routes.javascript.Admin.setTheme,
        
        api.routes.javascript.Comments.comment,
        api.routes.javascript.Datasets.comment,
        api.routes.javascript.Datasets.getTags,
        api.routes.javascript.Datasets.addTags,
        api.routes.javascript.Datasets.removeTag,
        api.routes.javascript.Datasets.removeTags,
        api.routes.javascript.Datasets.removeAllTags,
        api.routes.javascript.Files.comment,
        api.routes.javascript.Files.getTags,
        api.routes.javascript.Files.addTags,
        api.routes.javascript.Files.removeTags,
        api.routes.javascript.Files.removeAllTags,
        api.routes.javascript.Previews.upload,
        api.routes.javascript.Previews.uploadMetadata,
        api.routes.javascript.Sections.add,
        api.routes.javascript.Sections.comment,
        api.routes.javascript.Sections.getTags,
        api.routes.javascript.Sections.addTags,
        api.routes.javascript.Sections.removeTags,
        api.routes.javascript.Sections.removeAllTags,
        api.routes.javascript.Geostreams.searchSensors,
        api.routes.javascript.Geostreams.getSensorStreams,
        api.routes.javascript.Geostreams.searchDatapoints
      )
    ).as(JSON) 
  }
  
}

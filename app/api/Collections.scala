package api

import play.api.Logger
import play.api.Play.current
import models.{UUID, Collection}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import services.DatasetService
import services.CollectionService
import services.AdminsNotifierPlugin
import services.UserAccessRightsService
import scala.util.{Try, Success, Failure}
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import java.util.Date

/**
 * Manipulate collections.
 * 
 * @author Constantinos Sophocleous
 */
@Api(value = "/collections", listingPath = "/api-docs.json/collections", description = "Collections are groupings of datasets")
@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService, accessRights: UserAccessRightsService) extends ApiController {


  @ApiOperation(value = "Create a collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def createCollection() = SecuredAction(authorization=WithPermission(Permission.CreateCollections)) {
    request =>
      Logger.debug("Creating new collection")
      (request.body \ "name").asOpt[String].map {
        name =>
          (request.body \ "description").asOpt[String].map {
            description =>
              val c = Collection(name = name, description = description, created = new Date(), author = request.user, isPublic = Some(request.user.get.fullName.equals("Anonymous User")))
              accessRights.addPermissionLevel(request.user.get, c.id.stringify, "collection", "administrate")
              collections.insert(c) match {
                case Some(id) => {
                 Ok(toJson(Map("id" -> id)))
                }
                case None => Ok(toJson(Map("status" -> "error")))
              }
          }.getOrElse(BadRequest(toJson("Missing parameter [description]")))
      }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  @ApiOperation(value = "Add dataset to collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def attachDataset(collectionId: UUID, datasetId: UUID) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections), resourceId = Some(collectionId)) { request =>

    collections.addDataset(collectionId, datasetId) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  @ApiOperation(value = "Remove dataset from collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: String) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections), resourceId = Some(collectionId)) { request =>

    collections.removeDataset(collectionId, datasetId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  @ApiOperation(value = "Remove collection",
      notes = "Does not delete the individual datasets in the collection.",
      responseClass = "None", httpMethod = "POST")
  def removeCollection(collectionId: UUID) = SecuredAction(parse.anyContent,
                       authorization=WithPermission(Permission.DeleteCollections), resourceId = Some(collectionId)) { request =>
    collections.delete(collectionId)
    accessRights.removeResourceRightsForAll(collectionId.stringify, "collection")
    Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "List all collections",
      notes = "",
      responseClass = "None", httpMethod = "GET")
  def listCollections() = SecuredAction(parse.anyContent,
                                        authorization=WithPermission(Permission.ListCollections)) { request =>
    val list = for (collection <- collections.listCollections()) yield jsonCollection(collection)
    Ok(toJson(list))
  }
  
  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
               "created" -> collection.created.toString))
  }
}

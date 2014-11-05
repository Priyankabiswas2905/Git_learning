package controllers

import play.api.data.Form
import play.api.data.Forms._
import models.{UUID, Collection}
import java.util.Date
import play.api.Logger
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import api.WithPermission
import api.Permission
import play.api.Play.current
import javax.inject.{Singleton, Inject}
import services.{DatasetService, CollectionService}
import services.ElasticsearchPlugin
import services.AdminsNotifierPlugin


object ThumbnailFound extends Exception {}

@Singleton
class Collections @Inject()(datasets: DatasetService, collections: CollectionService) extends SecuredController {

  /**
   * New dataset form.
   */
  val collectionForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
      ((name, description) => Collection(name = name, description = description, created = new Date, author = null))
      ((collection: Collection) => Some((collection.name, collection.description)))
  )

  def newCollection() = SecuredAction(authorization = WithPermission(Permission.CreateCollections)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.newCollection(collectionForm))
  }

  /**
   * List collections.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(authorization = WithPermission(Permission.ListCollections)) {
    implicit request =>
      implicit val user = request.user
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
      var prev, next = ""
      var collectionList = List.empty[models.Collection]
      if (direction == "b") {
        collectionList = collections.listCollectionsBefore(date, limit)
      } else if (direction == "a") {
        collectionList = collections.listCollectionsAfter(date, limit)
      } else {
        badRequest
      }

      // latest object
      val latest = collections.latest()
      // first object
      val first = collections.first()
      var firstPage = false
      var lastPage = false
      if (latest.size == 1) {
        firstPage = collectionList.exists(_.id.equals(latest.get.id))
        lastPage = collectionList.exists(_.id.equals(first.get.id))
        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
        Logger.debug("first " + first.get.id + " last page " + lastPage)
      }
      if (collectionList.size > 0) {
        if (date != "" && !firstPage) {
          // show prev button
          prev = formatter.format(collectionList.head.created)
        }
        if (!lastPage) {
          // show next button
          next = formatter.format(collectionList.last.created)
        }
      }

      Ok(views.html.collectionList(collectionList, prev, next, limit))
    
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description, "created" -> collection.created.toString))
  }

  /**
   * Create collection.
   */
  def submit() = SecuredAction(authorization = WithPermission(Permission.CreateCollections)) {
    implicit request =>
      implicit val user = request.user
      user match {
	      case Some(identity) => {
	      
	      collectionForm.bindFromRequest.fold(
	        errors => BadRequest(views.html.newCollection(errors)),
	        collection => {
	          Logger.debug("Saving collection " + collection.name)
	          collections.insert(Collection(id = collection.id, name = collection.name, description = collection.description, created = collection.created, author = Some(identity)))
	          
	          // index collection
		            val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
		            current.plugin[ElasticsearchPlugin].foreach{_.index("data", "collection", collection.id, 
		                List(("name",collection.name), ("description", collection.description), ("created",dateFormat.format(new Date()))))}
	                    
	          // redirect to collection page
	          Redirect(routes.Collections.collection(collection.id))
	          current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Collection","added",collection.id.toString,collection.name)}
	          Redirect(routes.Collections.collection(collection.id))
	        })
	      }
	      case None => Redirect(routes.Collections.list()).flashing("error" -> "You are not authorized to create new collections.")
      }
  }

  /**
   * Collection.
   */
  def collection(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowCollection)) {
    implicit request =>
      Logger.debug(s"Showing collection $id")
      implicit val user = request.user
      collections.get(id) match {
        case Some(collection) => { 
          Logger.debug(s"Found collection $id")
          Ok(views.html.collectionofdatasets(datasets.listInsideCollection(id), collection))
        }
        case None => {
          Logger.error("Error getting collection " + id); BadRequest("Collection not found")
        }
      }
  }
}

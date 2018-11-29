package api

import javax.inject.Inject
import api.Permission.Permission
import models._
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import services._
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

class Tree @Inject()(
  events : EventService,
  vocabularies : VocabularyService,
  vocabularyTermService : VocabularyTermService,
  datasets : DatasetService,
  spaces: SpaceService,
  files: FileService,
  collections: CollectionService)  extends ApiController {

  def getChildrenOfNode(nodeId : Option[String], nodeType : String, mine: Boolean, shared: Boolean, public : Boolean, default: Boolean) = PrivateServerAction { implicit request =>
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]

    request.user match {
      case Some(user) => {
        if (nodeType == "root"){
          val result = getChildrenOfRoot(user, mine, shared, public, default)
          Ok(toJson(result))
        } else {
          Ok(toJson("unimplemented"))
        }
      }
      case None => {
        Ok(toJson("nouser"))
      }
    }
  }

  def getChildrenOfRoot(user: User, mine: Boolean, shared: Boolean, public: Boolean, default: Boolean): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]

    // default view - everything a user can see
    if (default) {
      var spaceList = spaces.listAccess(0, Set[Permission](Permission.ViewSpace), Some(user),true,false,false,false)
      for (space <- spaceList) {
        val num_collections_in_space = spaces.getCollectionsInSpace(Some(space.id.stringify)).size
        val num_datasets_in_space = spaces.getDatasetsInSpace(Some(space.id.stringify)).size
        val hasChildren = {
          if (num_collections_in_space + num_datasets_in_space > 0){
            true
          } else {
            false
          }
        }
        var currentJson = Json.obj("id" -> space.id, "text" -> space.name, "type" -> "space", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-hdd", "data" -> "none")
        children += currentJson
      }

      // only spaces, collections, datasets that are PUBLIC
    } else if (public){
      var spaceList = spaces.listAccess(0, Set[Permission](Permission.ViewSpace), Some(user),false,true,false,false)
      for (space <- spaceList) {
        val num_collections_in_space = spaces.getCollectionsInSpace(Some(space.id.stringify)).size
        val num_datasets_in_space = spaces.getDatasetsInSpace(Some(space.id.stringify)).size
        val hasChildren = {
          if (num_collections_in_space + num_datasets_in_space > 0){
            true
          } else {
            false
          }
        }
        var currentJson = Json.obj("id" -> space.id, "text" -> space.name, "type" -> "space", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-hdd", "data" -> "none")
        children += currentJson
      }

      // user is author, not shared with others
    } else if (mine && !shared){

      //user is author, shared with others
    } else if (mine && shared){

      //user not author, shared with author
    } else if (!mine && shared){

    }

    children.toList
  }

  def getChildrenOfSpace(space: ProjectSpace, user: User, mine: Boolean, shared: Boolean, public: Boolean, default: Boolean): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]

    // default view - everything a user can see
    if (default) {

      // only spaces, collections, datasets that are PUBLIC
    } else if (public){

      // user is author, not shared with others
    } else if (mine && !shared){

      //user is author, shared with others
    } else if (mine && shared){

      //user not author, shared with author
    } else if (!mine && shared){

    }
    children.toList
  }

  def getChildrenOfCollection(collection: Collection, user: User, mine: Boolean, shared: Boolean, public: Boolean): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    children.toList
  }

  def getChildrenOfDataset(dataset: Dataset, user: User, mine: Boolean, shared: Boolean, public: Boolean): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    children.toList
  }

}

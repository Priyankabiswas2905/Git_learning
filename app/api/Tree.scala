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

  def getChildrenOfNode(nodeId : Option[String], nodeType : String, mine: Boolean, shared: Boolean, public : Boolean, default: Boolean): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]

    children.toList
  }

  def getChildrenOfRoot(user: User, mine: Boolean, shared: Boolean, public: Boolean, default: Boolean): List[JsValue] = {
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

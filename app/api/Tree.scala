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
          nodeId match {
            case Some(id) => {
              if (nodeType == "space"){
                spaces.get(UUID(id)) match {
                  case Some(space) => {
                    val result = getChildrenOfSpace(space, user, mine, shared, public, default)
                    Ok(toJson(result))
                  }
                  case None => BadRequest("No space with id found")
                }
              } else if (nodeType == "collection"){
                collections.get(UUID(id)) match {
                  case Some(col) => {
                    val result = getChildrenOfCollection(col, user, mine, shared, public)
                    Ok(toJson(result))
                  }
                  case None => BadRequest("No collection with id found")
                }
              } else if (nodeType == "dataset") {
                datasets.get(UUID(id)) match {
                  case Some(ds) => {
                    val result = getChildrenOfDataset(ds, user, mine, shared, public)
                    Ok(toJson(result))
                  }
                  case None => BadRequest("No dataset with id found")
                }
              } else {
                BadRequest("No node type supplied")
              }
            }
            case None => BadRequest("No node id supplied")
          }
        }
      }
      case None => {
        BadRequest("No user supplied")
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
      var orphanCollections = getOrphanCollectionsNotInAnySpace(user)
      for (col <- orphanCollections){
        var hasChildren = false
        if (!col.child_collection_ids.isEmpty || col.datasetCount > 0){
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id"->col.thumbnail_id)
        var currentJson = Json.obj("id"->col.id,"text"->col.name,"type"->"collection", "children"->hasChildren,"data"->data)
        children += currentJson
      }
      var orphanDatasets = getOrphanDatasetsNotInAnySpace(user)
      for (ds <- orphanDatasets){
        var hasChildren = false
        if (ds.files.size > 0) {
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
        var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
        children+=currentJson
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
      // TODO public collections and datasets

      // user is author, not shared with others
    } else if (mine && !shared){

      //user is author, shared with others
    } else if (mine && shared){
      var spaceList = spaces.listAccess(0, Set[Permission](Permission.ViewSpace), Some(user),true,false,false,true).filter((p: ProjectSpace) => (p.creator == user))
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
      //user not author, shared with author
    } else if (!mine && shared){
      var spaceList = spaces.listAccess(0, Set[Permission](Permission.ViewSpace), Some(user),true,false,false,false).filter((p : ProjectSpace) => (p.creator != user))
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
      var orphanCollections = getOrphanCollectionsNotInAnySpace(user).filter((c : Collection) => (c.author != user))
      for (col <- orphanCollections){
        var hasChildren = false
        if (!col.child_collection_ids.isEmpty || col.datasetCount > 0){
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id"->col.thumbnail_id)
        var currentJson = Json.obj("id"->col.id,"text"->col.name,"type"->"collection", "children"->hasChildren,"data"->data)
        children += currentJson
      }
      var orphanDatasets = getOrphanDatasetsNotInAnySpace(user).filter((d: Dataset) => (d.author != user))
      for (ds <- orphanDatasets){
        var hasChildren = false
        if (ds.files.size > 0) {
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
        var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
        children+=currentJson
      }
    }

    children.toList
  }

  def getChildrenOfSpace(space: ProjectSpace, user: User, mine: Boolean, shared: Boolean, public: Boolean, default: Boolean): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]

    // default view - everything a user can see
    if (default) {
      var collectionsInSpace = collections.listSpace(0,space.id.stringify)
      for (col <- collectionsInSpace){
        var hasChildren = false
          if (!col.child_collection_ids.isEmpty || col.datasetCount > 0){
            hasChildren = true
          }
          var data = Json.obj("thumbnail_id"->col.thumbnail_id)
          var currentJson = Json.obj("id"->col.id,"text"->col.name,"type"->"collection", "children"->hasChildren,"data"->data)
          children += currentJson
      }
      var orphanDatasetsOfSpace = getOrphanDatasetsInSpace(space, user)
      for (ds <- orphanDatasetsOfSpace){
        var hasChildren = false
        if (ds.files.size > 0) {
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
        var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
        children+=currentJson
      }

      // only spaces, collections, datasets that are PUBLIC
    } else if (public){
      var collectionsInSpace = collections.listSpace(0,space.id.stringify)
      for (col <- collectionsInSpace){
        var hasChildren = false
        if (!col.child_collection_ids.isEmpty || col.datasetCount > 0){
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id"->col.thumbnail_id)
        var currentJson = Json.obj("id"->col.id,"text"->col.name,"type"->"collection", "children"->hasChildren,"data"->data)
        children += currentJson
      }
      // user is author, not shared with others
      val public_datasets = spaces.getDatasetsInSpace(Some(space.id.stringify)).filter((d : Dataset) => (d.isPublic))
      for (ds <- public_datasets){
        var hasChildren = false
        if (ds.files.size > 0) {
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
        var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
        children+=currentJson
      }
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

  //orphan collections and datasets
  def getOrphanCollectionsNotInAnySpace(user : User) : List[Collection] = {
    var collectionsNotInSpace = collections.listUser(0,Some(user),false,user).filter((c: Collection) => (c.spaces.isEmpty && c.parent_collection_ids.isEmpty))
    collectionsNotInSpace
  }

  def getOrphanDatasetsNotInAnySpace(user : User ) : List[Dataset] = {
    var datasetsNotInSpace = datasets.listUser(0,Some(user),false,user).filter((d: Dataset) => (d.spaces.isEmpty && d.collections.isEmpty))
    datasetsNotInSpace
  }

  def getOrphanDatasetsInSpace(space : ProjectSpace, user : User) : List[Dataset] = {
    var orphanDatasets : ListBuffer[Dataset] = ListBuffer.empty[Dataset]
    var datasetsInSpace = datasets.listSpace(0,space.id.stringify, Some(user))
    for (dataset <- datasetsInSpace){
      if (!collectionInSpaceContainsDataset(dataset,space)){
        orphanDatasets += dataset
      }
    }
    orphanDatasets.toList
  }

  def collectionInSpaceContainsDataset(dataset : Dataset, space : ProjectSpace) : Boolean = {
    var collectionInSpaceContainsDataset = false;
    var datasetCollectionIds = dataset.collections
    if (datasetCollectionIds.isEmpty){
      collectionInSpaceContainsDataset = false
      return collectionInSpaceContainsDataset
    }
    for (col_id <- datasetCollectionIds){
      collections.get(col_id) match {
        case Some(col) => {
          if (col.spaces.contains(space.id)){
            collectionInSpaceContainsDataset = true
            return collectionInSpaceContainsDataset
          }
        }
      }
    }
    return collectionInSpaceContainsDataset
  }


}

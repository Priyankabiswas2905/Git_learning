package api

import javax.inject.Inject
import api.Permission.Permission
import models._
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import services._
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

class TreeView @Inject()(
  events : EventService,
  vocabularies : VocabularyService,
  vocabularyTermService : VocabularyTermService,
  datasets : DatasetService,
  spaces: SpaceService,
  files: FileService,
  collections: CollectionService)  extends ApiController {


  def getChildrenOfNode(nodeId : Option[String], nodeType : String, mine : Boolean, shared : Boolean, public : Boolean) = PrivateServerAction{ implicit request =>

    val user = request.user

    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]

    if (nodeType == "space") {
      nodeId match {
        case Some(id) => {
          spaces.get(UUID(id)) match {
            case Some(space) => {
              var result = getChildrenOfSpace(space,user.get,mine,shared,public)
              Ok(toJson(result))
            }
            case None => BadRequest("No space found with id")
          }
        }
        case None => {
          BadRequest("No nodeId supplied")
        }
      }
    } else if (nodeType == "collection"){
      nodeId match {
        case Some(id) => {
          collections.get(UUID(id)) match {
            case Some(collection) => {
              var result = getChildrenOfCollection(collection,user.get,mine,shared,public)
              Ok(toJson(result))
            }
            case None => {
              BadRequest("No collection found with id")
            }
          }
        }
        case None => {
          BadRequest("No node id supplied")
        }
      }
    } else if (nodeType == "dataset"){
      nodeId match {
        case Some(id) => {
          datasets.get(UUID(id)) match {
            case Some(ds) => {
              var result = getChildrenOfDataset(ds,user)
              Ok(toJson(result))
            }
            case None => {
              BadRequest("No dataset found with id")
            }
          }
        }
        case None => {
          BadRequest("No id supplied")
        }
      }

    } else if (nodeType == "root") {
      var childrenOfRoot = getChildrenOfRoot(request.user.get,mine,shared,public)
      Ok(toJson(childrenOfRoot))
    } else {
      Ok(toJson(children.toList))
    }
  }




  def getChildrenOfDataset(dataset : Dataset, user  : Option[User]) : List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var files_inside = dataset.files
    for (f <- files_inside){
      files.get(f) match {
        case Some(file) => {
          file.thumbnail_id match {
            case Some(thumbnail) =>{
              var data = Json.obj("thumbnail_id"->file.thumbnail_id)
              var currentJson = Json.obj("id"->file.id,"text"->file.filename,"type"->"file","icon" -> "glyphicon glyphicon-file","data"->data)
              children += currentJson
            }
            case None => {
              var data = Json.obj("thumbnail_id"->file.thumbnail_id)
              var currentJson = Json.obj("id"->file.id,"text"->file.filename,"type"->"file","icon" -> "glyphicon glyphicon-file","data"->data)
              children += currentJson
            }
          }
        }
      }
    }
    children.toList
  }


  def getChildrenOfRoot(user : User, mine: Boolean, shared: Boolean, public : Boolean) : List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    if (mine){
      var spaceList = spaces.listUser(0,Some(user),false,user)
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
      var orphanCollections = getOrphanCollectionsNotInSpace(user)
      for (col <- orphanCollections){
        var hasChildren = false
        if (!col.child_collection_ids.isEmpty || col.datasetCount > 0){
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id"->col.thumbnail_id)
        var currentJson = Json.obj("id"->col.id,"text"->col.name,"type"->"collection", "children"->hasChildren,"data"->data)
        children += currentJson
      }

      var orphanDatasets = getOrphanDatasetsNotInSpace(user)
      for (ds <- orphanDatasets){
        var hasChildren = false
        if (ds.files.size > 0) {
          hasChildren = true
        }
        var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
        var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
        children+=currentJson
      }
      children.toList
    } else {
      if (public){
        var spaceList = spaces.listAccess(0, Set[Permission](Permission.ViewSpace),Some(user),false,true,false,true).filter(( s : ProjectSpace) => ((s.creator != user.id) && (s.isPublic)))
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
        //TODO get public orphan collections
        var orphanDatasets = getOrphaDatasetsPublic(user,false)
        for (ds <- orphanDatasets){
          var hasChildren = false
          if (ds.files.size > 0) {
            hasChildren = true
          }
          var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
          var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
          children+=currentJson
        }
      } else if (shared) {
        var spaceList = spaces.listAccess(0, Set[Permission](Permission.ViewSpace),Some(user),false,false,false,true).filter(( s : ProjectSpace) => (s.creator != user.id))
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
      } else {
        var spaceList = spaces.listAccess(0, Set[Permission](Permission.ViewSpace),Some(user),false,false,false,true)
        for (space <- spaceList){
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
      }
    }
    children.toList
  }

  def getChildrenOfSpace(space: ProjectSpace, user : User, mine : Boolean, shared: Boolean, public: Boolean) : List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    if (mine){
      if (shared){
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
        children.toList
      } else if (public){
        children.toList
      } else {
        var collectionsInSpace = collections.listSpace(0,space.id.stringify).filter(( c : Collection) => (c.author.id == user.id))
        for (col <- collectionsInSpace){
          var hasChildren = false
          if (col.author.id == user.id){
            if (!col.child_collection_ids.isEmpty || col.datasetCount > 0){
              hasChildren = true
            }
            var data = Json.obj("thumbnail_id"->col.thumbnail_id)
            var currentJson = Json.obj("id"->col.id,"text"->col.name,"type"->"collection", "children"->hasChildren,"data"->data)
            children += currentJson
          }
        }
        var orphanDatasetsInSpace = getOrphanDatasetsInSpace(space, user).filter( (d : Dataset) => (d.author.id == user.id))
        for (ds <- orphanDatasetsInSpace){
          val hasChildren = if (ds.files.size > 0){
            true
          } else {
            false
          }
          var data = Json.obj("thumbnail_id"->ds.thumbnail_id)
          var currentJson = Json.obj("id"->ds.id,"text"->ds.name,"type"->"dataset","children"->hasChildren,"icon"->"glyphicon glyphicon-briefcase","data"->data)
          children += currentJson
        }
      }
    } else {
      if (public){
        var collectionsInSpace = collections.listSpace(0,space.id.stringify)
        for (col <- collectionsInSpace){
          var hasChildren = false
          if (!col.child_collection_ids.isEmpty || col.datasetCount > 0) {
            hasChildren = true
          }
          var data = Json.obj("thumbnail_id"->col.thumbnail_id)
          var currentJson = Json.obj("id"->col.id,"text"->col.name,"type"->"collection", "children"->hasChildren,"data"->data)
          children += currentJson
        }
        var orphanDatasetsInSpace = getOrphanDatasetsInSpace(space, user).filter((d : Dataset) => (d.author.id != user.id))
        for (ds <- orphanDatasetsInSpace){
          val hasChildren = if (ds.files.size > 0){
            true
          } else {
            false
          }
          var data = Json.obj("thumbnail_id"->ds.thumbnail_id)
          var currentJson = Json.obj("id"->ds.id,"text"->ds.name,"type"->"dataset","children"->hasChildren,"icon"->"glyphicon glyphicon-briefcase","data"->data)
          children += currentJson
        }
      } else if (shared){
        var collectionsInSpace = collections.listSpace(0,space.id.stringify).filter(( c : Collection) => (c.author.id != user.id))
        for (col <- collectionsInSpace){
          var hasChildren = false
          if (col.author.id != user.id){
            if (!col.child_collection_ids.isEmpty || col.datasetCount > 0){
              hasChildren = true
            }
            var data = Json.obj("thumbnail_id"->col.thumbnail_id)
            var currentJson = Json.obj("id"->col.id,"text"->col.name,"type"->"collection", "children"->hasChildren,"data"->data)
            children += currentJson
          }
        }
        var orphanDatasetsInSpace = getOrphanDatasetsInSpace(space, user).filter((d : Dataset) => (d.author.id != user.id))
        for (ds <- orphanDatasetsInSpace) {
          val hasChildren = if (ds.files.size > 0) {
            true
          } else {
            false
          }
          var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
          var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
          children += currentJson
        }
      }
    }
    children.toList
  }


  def getChildrenOfCollection(collection: Collection, user: User, mine : Boolean, shared : Boolean, public : Boolean): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    if (mine) {
      if (shared) {
        val datasetsInCollection = datasets.listCollection(collection.id.stringify, Some(user))
        for (ds <- datasetsInCollection) {
          if (datasetSharedWithOthers(ds, user)) {
            var hasChildren = false
            if (ds.files.size > 0) {
              hasChildren = true
            }
            var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
            var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
            children += currentJson
          }
        }
        for (child_id <- collection.child_collection_ids){
          collections.get(child_id) match {
            case Some(child) => {
              if (collectionSharedWithOthers(child,user) && (child.trash == false)){
                var hasChildren = false
                if (child.author.id != user.id){
                  if (!child.child_collection_ids.isEmpty || child.datasetCount > 0){
                    hasChildren = true
                  }
                }
                var data = Json.obj("thumbnail_id"->child.thumbnail_id)
                var currentJson = Json.obj("id"->child.id,"text"->child.name,"type"->"collection", "children"->hasChildren,"data"->data)
                children += currentJson
              }
            }
          }
        }
        children.toList
      } else {
        val datasetsInCollection = datasets.listCollection(collection.id.stringify, Some(user)).filter(( d : models.Dataset) => ((d.author.id == user.id)))
        for (ds <- datasetsInCollection){
          val hasChildren = if (ds.files.size > 0){
            true
          } else {
            false
          }
          var data = Json.obj("thumbnail_id"->ds.thumbnail_id)
          var currentJson = Json.obj("id"->ds.id,"text"->ds.name,"type"->"dataset","children"->hasChildren,"icon"->"glyphicon glyphicon-briefcase","data"->data)
          children += currentJson
        }
        for (child_id <- collection.child_collection_ids){
          collections.get(child_id) match {
            case Some(child) => {
              if ((child.author.id == user.id) && (child.trash == false)){
                val hasChildren = if (!child.child_collection_ids.isEmpty || child.datasetCount > 0){
                  true
                } else {
                  false
                }
                var data = Json.obj("thumbnail_id"->child.thumbnail_id)
                var currentJson = Json.obj("id"->child.id,"text"->child.name,"type"->"collection", "children"->hasChildren,"data"->data)
                children += currentJson
              }
            }
          }
        }
        children.toList
      }
    } else {
      if (public){
        val datasetsInCollection = datasets.listCollection(collection.id.stringify, Some(user))
        for (ds <- datasetsInCollection) {
          if (datasetPublic(ds) && (ds.author.id != user.id)) {
            var hasChildren = false
            if (ds.files.size > 0) {
              hasChildren = true
            }
            var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
            var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
            children += currentJson
          }
        }
        for (child_id <- collection.child_collection_ids){
          collections.get(child_id) match {
            case Some(child) => {
              if (collectionPublic(child) && (child.trash == false) && (child.author.id != user.id)){
                var hasChildren = false
                if (child.author.id != user.id){
                  if (!child.child_collection_ids.isEmpty || child.datasetCount > 0){
                    hasChildren = true
                  }
                }
                var data = Json.obj("thumbnail_id"->child.thumbnail_id)
                var currentJson = Json.obj("id"->child.id,"text"->child.name,"type"->"collection", "children"->hasChildren,"data"->data)
                children += currentJson
              }
            }
          }
        }
        children.toList
      } else if (shared) {
        val datasetsInCollection = datasets.listCollection(collection.id.stringify, Some(user)).filter((d : Dataset) => (d.author.id != user.id))
        for (ds <- datasetsInCollection) {
          var hasChildren = false
          if (ds.files.size > 0) {
            hasChildren = true
          }
          var data = Json.obj("thumbnail_id" -> ds.thumbnail_id)
          var currentJson = Json.obj("id" -> ds.id, "text" -> ds.name, "type" -> "dataset", "children" -> hasChildren, "icon" -> "glyphicon glyphicon-briefcase", "data" -> data)
          children += currentJson
        }
        for (child_id <- collection.child_collection_ids){
          collections.get(child_id) match {
            case Some(child) => {
              if (collectionSharedWithMe(child,user) && (child.trash == false)){
                var hasChildren = false
                if (child.author.id != user.id){
                  if (!child.child_collection_ids.isEmpty || child.datasetCount > 0){
                    hasChildren = true
                  }
                }
                var data = Json.obj("thumbnail_id"->child.thumbnail_id)
                var currentJson = Json.obj("id"->child.id,"text"->child.name,"type"->"collection", "children"->hasChildren,"data"->data)
                children += currentJson
              }
            }
          }
        }
        children.toList
      }
    }
    children.toList
  }

  def getOrphanCollectionsNotInSpace(user : User) : List[Collection] = {
    var collectionsNotInSpace = collections.listUser(0,Some(user),false,user).filter((c: Collection) => (c.spaces.isEmpty && c.parent_collection_ids.isEmpty))
    collectionsNotInSpace
  }

  def getOrphanDatasetsNotInSpace(user : User ) : List[Dataset] = {
    var datasetsNotInSpace = datasets.listUser(0,Some(user),false,user).filter((d: Dataset) => (d.spaces.isEmpty && d.collections.isEmpty))
    datasetsNotInSpace
  }

  def getOrphaDatasetsPublic(user: User, mine : Boolean) : List[Dataset] = {
    var datasetsPublic = datasets.listAccess(0,Set[Permission](Permission.ViewDataset),Some(user),false,true,false).filter((d : Dataset) => (d.isPublic))
    if (mine){
      datasetsPublic
    } else {
      datasetsPublic.filter((d: Dataset)=>(d.author.id != user.id))
    }
  }

  def getOrphanDatasetsInSpace(space : ProjectSpace, user : User) : List[Dataset] = {
    var orphanDatasets : ListBuffer[Dataset] = ListBuffer.empty[Dataset]
    var datasetsInSpace = datasets.listSpace(0,space.id.stringify, Some(user))
    for (dataset <- datasetsInSpace){
      if (!datasetHasCollectionInSpace(dataset,space)){
        orphanDatasets += dataset
      }
    }
    orphanDatasets.toList
  }

  def datasetHasCollectionInSpace(dataset : Dataset, space : ProjectSpace) : Boolean = {
    var hasCollectionInSpace = false;
    var datasetCollectionIds = dataset.collections
    if (datasetCollectionIds.isEmpty){
      hasCollectionInSpace = false
      return hasCollectionInSpace
    }
    for (col_id <- datasetCollectionIds){
      collections.get(col_id) match {
        case Some(col) => {
          if (col.spaces.contains(space.id)){
            hasCollectionInSpace = true
            return hasCollectionInSpace
          }
        }
      }
    }
    return hasCollectionInSpace
  }

  def datasetPublic(dataset: Dataset) : Boolean = {
    var public = false;
    val spacesOfDataset = dataset.spaces
    for (space <- spacesOfDataset){
      spaces.get(space) match {
        case Some(s) => {
          if (s.isPublic){
            public = true;
            return public
          }
        }
        case None =>
      }
    }
    return public
  }

  def collectionPublic(collection: Collection) : Boolean = {
    var public = false;
    val spacesOfCollection = collection.spaces
    for (space <- spacesOfCollection){
      spaces.get(space) match {
        case Some(s) => {
          if (s.isPublic){
            public = true;
            return public
          }
        }
        case None =>
      }
    }
    return public
  }

  def datasetSharedWithOthers(dataset : Dataset, user : User): Boolean = {
    val spacesOfDataset = dataset.spaces
    for (space <- spacesOfDataset){
      if (spaceSharedWithOthers(space)){
        if (dataset.author.id == user.id){
          return true
        } else {
          return false
        }
      }
    }
    return false
  }

  def datasetSharedWithMe(dataset : Dataset, user : User): Boolean = {
    val spacesOfDataset = dataset.spaces
    for (space <- spacesOfDataset){
      if (spaceSharedWithOthers(space)){
        if (dataset.author.id == user.id){
          return false
        } else {
          return true
        }
      }
    }
    return false
  }

  def collectionSharedWithOthers(collection : Collection, user : User): Boolean = {
    val spacesOfCollection = collection.spaces
    for (space <- spacesOfCollection){
      if (spaceSharedWithOthers(space)){
        if (collection.author.id == user.id){
          return true
        } else {
          return false
        }
      }
    }
    return false
  }

  def collectionSharedWithMe(collection : Collection, user : User): Boolean = {
    val spacesOfCollection = collection.spaces
    for (space <- spacesOfCollection){
      if (spaceSharedWithOthers(space)){
        if (collection.author.id == user.id){
          return false
        } else {
          return true
        }
      }
    }
    return false
  }

  def spaceSharedWithOthers(spaceId : UUID): Boolean ={
    spaces.get(spaceId) match {
      case Some(space) => {
        if (space.userCount > 1 && !space.isPublic){
          return true
        } else {
          return false
        }
      }
      case None => false
    }
  }
}

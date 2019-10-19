package services

import api.Permission
import api.Permission.Permission
import javax.inject.{Inject, Singleton}
import models._
import play.api.libs.json.{JsValue, Json}

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

/**
  * Service for creating a file tree view for Spaces, Collections, Datasets.
  *
  *
  */

class TreeService @Inject()(
                             datasets: DatasetService,
                             fileService: FileService,
                             folderService: FolderService,
                             collections: CollectionService,
                             userService: UserService,
                             spaceService: SpaceService,
                             events:EventService
                           ) {

  def getChildrenOfNode(nodeType: String, nodeId: Option[String], mine: Boolean, user: User): List[JsValue] = {
    var children : List[JsValue] = List.empty[JsValue]
    if (nodeType == "space"){
      nodeId match {
        case Some(id) => {
          spaceService.get(UUID(id)) match {
            case Some(space) => {
              children = getChildrenOfSpace(Some(space), mine, user)
            }
            case None =>
          }
        }
        case None => children = getSpaces(mine, user)
      }
    } else if (nodeType == "collection") {
      nodeId match {
        case Some(id) => {
          collections.get(UUID(id)) match {
            case Some(col) => {
              children = getChildrenOfCollection(Some(col), mine, user)
            }
            case None =>
          }
        }
        case None => children = getCollections(mine, user)
      }
    } else if (nodeType == "dataset") {
      nodeId match {
        case Some(id) => {
          datasets.get(UUID(id)) match {
            case Some(ds) => {
              children = getChildrenOfDataset(Some(ds), mine, user)
            }
            case None =>
          }
        }
        case None => children = getDatasets(mine, user)
      }
    } else if (nodeType == "folder"){
      nodeId match {
        case Some(id) => {
          folderService.get(UUID(id)) match {
            case Some(folder) => {
              children = getChildrenOfFolder(folder, mine, user)
            }
            case None =>
          }
        }
        case None =>
      }
    } else if (nodeType == "root"){
      children = getSpacesAndOrphanCollectionsDatasets(mine, user)
    }
    children
  }

  def getChildrenOfSpace(space: Option[ProjectSpace], mine: Boolean, user : User): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]

    var collectionsInSpace = spaceService.getCollectionsInSpace(Some(space.get.id.stringify),None)
    var datasetsInSpace = spaceService.getDatasetsInSpace(Some(space.get.id.stringify),None)
    // filters datasets that have a collection in the space
    datasetsInSpace = datasetsInSpace.filter((d: Dataset) => (!datasetHasCollectionInSpace(d,space.get)))
    if (mine){
      collectionsInSpace = collectionsInSpace.filter((c: Collection) => (c.author.id == user.id))
      datasetsInSpace = datasetsInSpace.filter((d: Dataset) => (d.author.id == user.id))
    } else {

    }
    for (d <- datasetsInSpace){
      var dsjson : JsValue = datasetJson(d)
      children += dsjson
    }
    for (c <- collectionsInSpace){
      var coljson : JsValue = collectionJson(c)
      children += coljson
    }
    children.toList
  }

  def getChildrenOfCollection(collection: Option[Collection], mine: Boolean, user : User): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var child_collections = collections.listChildCollections(collection.get.id)
    var datasets_in_collection = datasets.listCollection(collection.get.id.stringify, Some(user))

    for (child <- child_collections){
      var childColJson : JsValue = collectionJson(child)
      children += childColJson
    }
    for (ds <- datasets_in_collection){
      var dsJson : JsValue = datasetJson(ds)
      children += dsJson
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

  def getChildrenOfDataset(dataset: Option[Dataset], mine: Boolean, user : User): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var ds_file_ids = dataset.get.files
    for (f <- ds_file_ids){
      fileService.get(f) match {
        case Some(file) => {
          var fjson : JsValue = fileJson(file)
          children += fjson
        }
        case None =>
      }
    }
    var ds_folder_ids = dataset.get.folders
    for (ds_folder_id <- ds_folder_ids){
      folderService.get(ds_folder_id) match {
        case Some(folder) => {
          var fjson :JsValue  = folderJson(folder)
          children += fjson
        }
        case None =>
      }
    }
    children.toList
  }

  def getChildrenOfFolder(folder: Folder, mine: Boolean, user: User) : List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]

    var subfolders_ids = folder.folders
    for (subfolder_id <- subfolders_ids){
      folderService.get(subfolder_id) match {
        case Some(subfolder) => {
          var sfjson = folderJson(subfolder)
          children += sfjson
        }
        case None =>
      }
    }

    var file_ids = folder.files
    for (f <- file_ids){
      fileService.get(f) match {
        case Some(file) => {
          var fjson : JsValue = fileJson(file)
          children += fjson
        }
        case None =>
      }
    }

    children.toList
  }

  def getSpacesAndOrphanCollectionsDatasets(mine: Boolean, user : User) : List[JsValue] = {
    var root_level_nodes : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var spaces = spaceService.listAccess(0,Set[Permission](Permission.ViewSpace),Some(user),true,true,false,false)
    if (mine){
      spaces = spaces.filter((s: ProjectSpace) => (s.creator == user.id))
    }
    for (s <- spaces){
      var s_json = spaceJson(s)
      root_level_nodes += s_json
    }
    var orphan_cols = getOrphanCollectionsNotInSpace(user)
    for (c <- orphan_cols){
      var c_json = collectionJson(c)
      root_level_nodes += c_json
    }

    var orphan_ds = getOrphanDatasetsNotInSpace(user)
    for (d <- orphan_ds){
      var d_json = datasetJson(d)
      root_level_nodes += d_json
    }
    root_level_nodes.toList
  }

  def getSpaces(mine : Boolean, user: User) : List[JsValue] = {
    var rootSpaces : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var spaces = spaceService.listAccess(0,Set[Permission](Permission.ViewSpace),Some(user),true,true,false,false)
    if (mine){
      spaces = spaces.filter((s: ProjectSpace) => (s.creator == user.id))
    }
    for (s <- spaces){
      var s_json = spaceJson(s)
      rootSpaces += s_json
    }
    rootSpaces.toList
  }

  def getDatasets(mine : Boolean, user: User) : List[JsValue] = {
    var visibleDatasets : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var ds = datasets.listAccess(999,Set[Permission](Permission.ViewDataset),Some(user),true,true,false)
    // ds = ds.filter((d: Dataset) => (d.trash == false))
    if (mine){
      ds = ds.filter((d: Dataset) => (d.author.id == user.id))
    }
    for (d <- ds){
      var d_json = datasetJson(d)
      visibleDatasets += d_json
    }
    visibleDatasets.toList
  }

  def getCollections(mine: Boolean, user: User) : List[JsValue] = {
    var cols = collections.listAccess(0,Set[Permission](Permission.ViewCollection),Some(user),true,true,false)
    // cols = cols.filter((c : Collection) => (c.trash == false))
    if (mine) {
      cols = cols.filter((c: Collection) => (c.author.id == user.id))
    }
    var visibleCollection : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    for (col <-cols){
      visibleCollection += collectionJson(col)

    }
    visibleCollection.toList
  }

  private def collectionJson(collection: Collection) : JsValue = {
    var hasChildren = false
    if (collection.child_collection_ids.size > 0 || collection.datasetCount > 0){
      hasChildren = true
    }
    Json.obj("id" -> collection.id.toString, "name" -> collection.name, "text" -> collection.name,
      "authorId" -> collection.author.id, "children"->hasChildren,"type"->"collection", "data"->"collection")
  }

  private def datasetJson(dataset: Dataset) : JsValue = {
    Json.obj("id" -> dataset.id.toString, "name" -> dataset.name,"text"->dataset.name, "authorId" -> dataset.author.id,
      "spaces" -> dataset.spaces, "type"->"dataset","data"->"dataset", "icon"->"glyphicon glyphicon-briefcase")

  }

  private def spaceJson(space: ProjectSpace) : JsValue = {
    var hasChildren = false
    if (space.datasetCount > 0 || space.collectionCount > 0){
      hasChildren = true
    }
    Json.obj("id"-> space.id.toString, "name"->space.name ,"text"->space.name , "children"->hasChildren, "type"->"space","data"->"space", "icon" -> "glyphicon glyphicon-hdd")
  }

  private def fileJson(file: File) : JsValue = {
    Json.obj("id"->file.id, "name"->file.filename, "text"->file.filename, "type"->"file","data"->"file","icon"-> "glyphicon glyphicon-file")
  }

  private def folderJson(folder: Folder) : JsValue = {
    Json.obj("id"->folder.id, "name"->folder.name,"text"->folder.name, "type"->"folder","icon"->"glyphicon glyphicon-folder")

  }

  private def datasetHasCollectionInSpace(dataset : Dataset, space : ProjectSpace) : Boolean = {
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

}

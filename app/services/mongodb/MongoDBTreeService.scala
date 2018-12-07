package services.mongodb

import api.Permission
import api.Permission.Permission
import javax.inject.{Inject, Singleton}
import models._
import play.api.libs.json.{JsValue, Json}
import services._

import scala.collection.mutable.ListBuffer

@Singleton
class MongoDBTreeService @Inject() (
  datasets: DatasetService,
  fileService: FileService,
  folderService: FolderService,
  collections: CollectionService,
  userService: UserService,
  spaceService: SpaceService,
  events:EventService)  extends TreeService {

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
      var datasetJson = datasetJson(d)
      children += datasetJson
    }
    for (c <- collectionsInSpace){
      var collectionJson = collectionJson(c)
      children += collectionJson
    }
    children.toList
  }

  def getChildrenOfCollection(collection: Option[Collection], mine: Boolean, user : User): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var child_collections = collections.listChildCollections(collection.get.id)
    var datasets_in_collection = datasets.listCollection(collection.get.id.stringify, Some(user))

    for (child <- child_collections){
      var childColJson = collectionJson(child)
      children += childColJson
    }
    for (ds <- datasets_in_collection){
      var dsJson = datasetJson(ds)
      children += dsJson
    }

    children.toList
  }

  def getChildrenOfDataset(dataset: Option[Dataset], mine: Boolean, user : User): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var ds_file_ids = dataset.get.files
    for (f <- ds_file_ids){
      fileService.get(f) match {
        case Some(file) => {
          var fileJson = fileJson(file)
          children += fileJson
        }
        case None =>
      }
    }
    var ds_folder_ids = dataset.get.folders
    for (ds_folder_id <- ds_folder_ids){
      folderService.get(ds_folder_id) match {
        case Some(folder) => {
          var folderJson = folderJson(folder)
          children += folderJson
        }
        case None =>
      }
    }
    children.toList
  }

  def getSpaces(mine : Boolean, user: User) : List[JsValue] = {
    var rootSpaces : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var spaces = spaceService.listAccess(0,Set[Permission](Permission.ViewSpace),Some(user),true,true,false,false)
    if (mine){
      spaces = spaces.filter((s: ProjectSpace) => (s.creator == user.id))
    }
    rootSpaces.toList
  }

  def getDatasets(mine : Boolean, user: User) : List[JsValue] = {
    var visibleDatasets : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var ds = datasets.listAccess(0,Set[Permission](Permission.ViewDataset),Some(user),true,true,false)
    // ds = ds.filter((d: Dataset) => (d.trash == false))
    if (mine){
      ds = ds.filter((d: Dataset) => (d.author == user))
    }
    visibleDatasets.toList
  }

  def getCollections(mine: Boolean, user: User) : List[JsValue] = {
    var cols = collections.listAccess(0,Set[Permission](Permission.ViewCollection),Some(user),true,true,false)
    // cols = cols.filter((c : Collection) => (c.trash == false))
    if (mine) {
      cols = cols.filter((c: Collection) => (c.author == user))
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
    Json.obj("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
      "created" -> collection.created.toString, "thumbnail" -> collection.thumbnail_id, "authorId" -> collection.author.id, "hasChildren"->hasChildren,"type"->"collection")
  }

  private def datasetJson(dataset: Dataset) : JsValue = {
    Json.obj("id" -> dataset.id.toString, "name" -> dataset.name, "description" -> dataset.description,
      "created" -> dataset.created.toString, "thumbnail" -> dataset.thumbnail_id, "authorId" -> dataset.author.id, "spaces" -> dataset.spaces)

  }

  private def spaceJson(space: ProjectSpace) : JsValue = {
    var hasChildren = false
    if (space.datasetCount > 0 || space.collectionCount > 0){
      hasChildren = true
    }
    Json.obj("id"-> space.id.toString, "name"->space.name ,"hasChildren"->hasChildren)
  }

  private def fileJson(file: File) : JsValue = {
    Json.obj("id"->file.id, "name"->file.filename, "type"->"file")
  }

  private def folderJson(folder: Folder) : JsValue = {
    Json.obj("id"->folder.id, "name"->folder.name, "type"->"folder")

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

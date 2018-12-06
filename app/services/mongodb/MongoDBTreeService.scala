package services.mongodb

import api.Permission
import api.Permission.Permission
import javax.inject.{Inject, Singleton}
import models._
import play.api.libs.json.JsValue
import services._

import scala.collection.mutable.ListBuffer

@Singleton
class MongoDBTreeService @Inject() (
  datasets: DatasetService,
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
    if (mine){

    } else {

    }
    children.toList
  }

  def getChildrenOfCollection(collection: Option[Collection], mine: Boolean, user : User): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    children.toList
  }

  def getChildrenOfDataset(dataset: Option[Dataset], mine: Boolean, user : User): List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
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
    if (mine){
      ds = ds.filter((d: Dataset) => (d.author == user))
    }
    visibleDatasets.toList
  }

  def getCollections(mine: Boolean, user: User) : List[JsValue] = {
    var cols = collections.listAccess(0,Set[Permission](Permission.ViewCollection),Some(user),true,true,false)
    if (mine) {
      cols = cols.filter((c: Collection) => (c.author == user))
    }
    var visibleCollection : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    visibleCollection.toList
  }


}

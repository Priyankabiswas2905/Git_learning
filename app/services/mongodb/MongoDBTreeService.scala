package services.mongodb

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
        case None =>
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
        case None =>
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
        case None =>
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

}

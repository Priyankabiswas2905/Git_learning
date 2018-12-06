package services

import models._
import play.api.libs.json.{JsValue}

/**
  * Service for creating a file tree view for Spaces, Collections, Datasets.
  *
  *
  */

trait TreeService {

  def getChildrenOfNode(nodeType: String, nodeId: Option[String], mine: Boolean, user : User) :List[JsValue]

  def getChildrenOfSpace(space : Option[ProjectSpace], mine : Boolean, user : User) :List[JsValue]

  def getSpaces(mine : Boolean, user: User) : List[JsValue]

  def getChildrenOfCollection(collection: Option[Collection], mine : Boolean, user : User) :List[JsValue]

  def getCollections(mine: Boolean, user : User) : List[JsValue]

  def getChildrenOfDataset(dataset: Option[Dataset], mine : Boolean, user : User) :List[JsValue]

  def getDatasets(mine: Boolean, user: User) : List[JsValue]
}

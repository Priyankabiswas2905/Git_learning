package services

import java.io.InputStream
import java.util.Date

import models._
import com.mongodb.casbah.Imports._
import models.FileStatus.FileStatus
import play.api.libs.json.{JsArray, JsObject, JsValue}

/**
  * Service for creating a file tree view for Spaces, Collections, Datasets.
  *
  *
  */

trait TreeService {

  def getChildrenOfNode(nodeType: String, nodeId: Option[String], mine: Boolean, user : User) :List[JsValue]

  def getChildrenOfSpace(space : Option[ProjectSpace], mine : Boolean, user : User) :List[JsValue]

  def getChildrenOfCollection(collection: Option[Collection], mine : Boolean, user : User) :List[JsValue]

  def getChildrenOfDataset(dataset: Option[Dataset], mine : Boolean, user : User) :List[JsValue]

}

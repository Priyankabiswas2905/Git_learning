package services

import models._

import scala.collection.immutable.List

trait IndexService {
  def resetIndex(): Unit

  def isConnected: Boolean

  def connectionInfo(): String

  def add(collection: Collection, recursive: Boolean): Unit

  def add(dataset: Dataset, recursive: Boolean): Unit

  def add(file: File): Unit

  def add(file: TempFile): Unit

  def add(section: Section): Unit

  def delete(resource: ResourceRef): Unit

  def delete(collection: Collection)

  def delete(dataset: Dataset)

  def delete(file: File)

  def delete(file: TempFile): Unit

  def delete(section: Section)

  def listTags(): Map[String, Long]

  def listTags(resourceType: String): Map[String, Long]

  def listMetadataFields(query: String): List[String]

  def search(query: String): List[ResourceRef]

  def searchAdvanced(query: String): List[ResourceRef]
}

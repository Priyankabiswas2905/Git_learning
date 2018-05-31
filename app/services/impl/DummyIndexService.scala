package services.impl

import models._
import services.IndexService

import scala.collection.immutable.List

/** dummy index class, does nothing */
class DummyIndexService extends IndexService {
  override def resetIndex() {}

  override def isConnected: Boolean = false

  override def connectionInfo(): String = "No connected to index service"

  override def add(collection: Collection, recursive: Boolean) {}

  override def add(dataset: Dataset, recursive: Boolean) {}

  override def add(file: File) {}

  override def add(file: TempFile) {}

  override def add(section: Section) {}

  override def delete(resource: ResourceRef) {}

  override def delete(collection: Collection) {}

  override def delete(dataset: Dataset) {}

  override def delete(file: TempFile) {}

  override def delete(file: File) {}

  override def delete(section: Section) {}

  override def listTags(): Map[String, Long] = Map.empty

  override def listTags(resourceType: String): Map[String, Long] = Map.empty

  override def listMetadataFields(query: String): List[String] = List.empty

  override def search(query: String): List[ResourceRef] = List.empty

  override def searchAdvanced(query: String): List[ResourceRef] = List.empty
}

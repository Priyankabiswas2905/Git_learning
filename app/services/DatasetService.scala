/**
 *
 */
package services
import models.Dataset
import models.Collection

/**
 * Generic dataset service.
 * 
 * @author Luigi Marini
 *
 */
abstract class DatasetService {
  
  /**
   * List all datasets in the system.
   */
  def listDatasets(): List[Dataset]
  
  /**
   * List all datasets in the system in reverse chronological order.
   */
  def listDatasetsChronoReverse(): List[Dataset]
  
  /**
   * List datasets after a specified date.
   */
  def listDatasetsAfter(date: String, limit: Int): List[Dataset]
  
  /**
   * List datasets before a specified date.
   */
  def listDatasetsBefore(date: String, limit: Int): List[Dataset]
  
  
  /**
   * Get dataset.
   */
  def get(id: String): Option[Dataset]
  
  /**
   * 
   */
  def listInsideCollection(collectionId: String) : List[Dataset]
  
  /**
   * 
   */
  def isInCollection(dataset: Dataset, collection: Collection): Boolean
  
  /**
   * 
   */
  def getFileId(datasetId: String, filename: String): Option[String]
  
  def modifyRDFOfMetadataChangedDatasets()
  
  def modifyRDFUserMetadata(id: String, mappingNumber: String="1")
}
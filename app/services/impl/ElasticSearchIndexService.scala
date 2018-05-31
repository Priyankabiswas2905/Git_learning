package services.impl

import java.io.IOException

import models._
import org.apache.http.HttpHost
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.bulk.{BulkRequest, BulkResponse}
import org.elasticsearch.action.delete.{DeleteRequest, DeleteResponse}
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.{RestClient, RestHighLevelClient}
import play.api.Logger
import services._
import util.SearchUtils

import scala.collection.immutable.List

// TODO when higher level scala use a single bulk processor
// TODO use AKKA with inbox to have a list of operations
// TODO have a persistent list of operations that need to be done
class ElasticSearchIndexService extends IndexService {
  private lazy val files: FileService = DI.injector.getInstance(classOf[FileService])
  private lazy val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  private lazy val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  private lazy val comments: CommentService = DI.injector.getInstance(classOf[CommentService])

  // connect to elastic seaerch first time we use the client variable
  // TODO check what happens if elasticsearch index goes down, how do we recover?
  private lazy val client = {
    // TODO hardcoded to use localhost 9200
    // docker run -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:6.2.4
    val x = new RestHighLevelClient(
      RestClient.builder(
        new HttpHost("localhost", 9200, "http")))

    // always create index, ignore failure
    try {
      val request = new CreateIndexRequest("clowder")
      val response = x.indices().create(request)
      if (!response.isAcknowledged) {
        Logger.error("Failures during creation of index.")
      }
    } catch {
      case e: IOException => Logger.error("Failures during creation of index.")
    }

    // return the client
    x
  }

  // bulk listener for response of bulk actions
  private lazy val bulkListener = new ActionListener[BulkResponse]() {
    override def onResponse(response: BulkResponse ) {
      Logger.debug(s"Finished ${response.getItems} bulk operation in ${response.getTook} seconds")
      if (response.hasFailures) {
        Logger.error("Failures during execution of bulk operation.\n" + response.buildFailureMessage())
      }
    }

    override def onFailure(exception: Exception) {
      Logger.error("Failure to execute.", exception)
    }
  }

  override def isConnected: Boolean = client.ping()

  override def connectionInfo(): String = {
    val info = client.info()
    s"connected to ${info.getClusterName}, version ${info.getVersion}"
  }

  override def resetIndex() {
    try {
      val request = new DeleteIndexRequest("clowder")
      val response = client.indices().delete(request)
      if (!response.isAcknowledged) {
        Logger.error("Failures during creation of index.")
      }
    } catch {
      case e: IOException => Logger.error("Failures during deletion of index.")
    }

    try {
      val request = new CreateIndexRequest("clowder")
      val response = client.indices().create(request)
      if (!response.isAcknowledged) {
        Logger.error("Failures during creation of index.")
      }
    } catch {
      case e: IOException => Logger.error("Failures during creation of index.")
    }
  }

  // ----------------------------------------------------------------------
  // SEARCH INDEX
  // ----------------------------------------------------------------------

  // TODO needs implemetnation
  override def listTags(): Map[String, Long] = Map.empty

  override def listTags(resourceType: String): Map[String, Long] = Map.empty

  override def listMetadataFields(query: String): List[String] = List.empty

  override def search(query: String): List[ResourceRef] = List.empty

  override def searchAdvanced(query: String): List[ResourceRef] = List.empty

  // ----------------------------------------------------------------------
  // ADD TO INDEX
  // ----------------------------------------------------------------------

  override def add(collection: Collection, recursive: Boolean) {
    val bulkRequest = new BulkRequest()
    indexHelper(collection, recursive, bulkRequest)
    client.bulkAsync(bulkRequest, bulkListener)
  }

  override def add(dataset: Dataset, recursive: Boolean) {
    val bulkRequest = new BulkRequest()
    indexHelper(dataset, recursive, bulkRequest)
    client.bulkAsync(bulkRequest, bulkListener)
  }

  override def add(file: File) {
    val bulkRequest = new BulkRequest()
    indexHelper(file, bulkRequest)
    client.bulkAsync(bulkRequest, bulkListener)
  }

  override def add(file: TempFile) {
    val bulkRequest = new BulkRequest()
    indexHelper(file, bulkRequest)
    client.bulkAsync(bulkRequest, bulkListener)
  }

  override def add(section: Section) {
    val bulkRequest = new BulkRequest()
    indexHelper(section, bulkRequest)
    client.bulkAsync(bulkRequest, bulkListener)
  }

  // private functions to be called recursively
  private def indexHelper(collection: Collection, recursive: Boolean, bulkRequest: BulkRequest) {
    if (recursive) {
      collection.child_collection_ids.foreach(collections.get(_).foreach(indexHelper(_, recursive, bulkRequest)))
      datasets.listCollection(collection.id.stringify).foreach(indexHelper(_, recursive, bulkRequest))
    }
    SearchUtils.getElasticsearchObject(collection).foreach(indexHelper(_, bulkRequest))
  }

  private def indexHelper(dataset: Dataset, recursive: Boolean, bulkRequest: BulkRequest) {
    if (recursive) {
      dataset.files.foreach(files.get(_).foreach(indexHelper(_, bulkRequest)))
    }
    SearchUtils.getElasticsearchObject(dataset).foreach(indexHelper(_, bulkRequest))
  }

  private def indexHelper(file: File, bulkRequest: BulkRequest){
    file.sections.foreach(indexHelper(_, bulkRequest))
    SearchUtils.getElasticsearchObject(file).foreach(indexHelper(_, bulkRequest))
  }

  private def indexHelper(file: TempFile, bulkRequest: BulkRequest){
    SearchUtils.getElasticsearchObject(file).foreach(indexHelper(_, bulkRequest))
  }

  private def indexHelper(section: Section, bulkRequest: BulkRequest) {
    SearchUtils.getElasticsearchObject(section).foreach(indexHelper(_, bulkRequest))
  }

  private def indexHelper(elasticsearchObject: ElasticsearchObject, bulkRequest: BulkRequest): BulkRequest = {
    val indexRequest = new IndexRequest("clowder",
      elasticsearchObject.resource.resourceType.name,
      elasticsearchObject.resource.id.stringify)
    indexRequest.source(elasticsearchObject)
    bulkRequest.add(indexRequest)
    bulkRequest
  }

  // ----------------------------------------------------------------------
  // DELETE FROM INDEX
  // ----------------------------------------------------------------------

  override def delete(collection: Collection): Unit = {
    delete(ResourceRef(ResourceRef.collection, collection.id))
  }

  override def delete(dataset: Dataset): Unit = {
    delete(ResourceRef(ResourceRef.dataset, dataset.id))
  }

  override def delete(file: File): Unit = {
    delete(ResourceRef(ResourceRef.file, file.id))
  }

  override def delete(file: TempFile): Unit = {
    delete(ResourceRef(ResourceRef.file, file.id))
  }

  override def delete(section: Section): Unit = {
    delete(ResourceRef(ResourceRef.section, section.id))
  }

  override def delete(resource: ResourceRef) {
    val deleteRequest = new DeleteRequest("clowder",
      resource.resourceType.name,
      resource.id.stringify)
    client.deleteAsync(deleteRequest, new ActionListener[DeleteResponse] {
      override def onResponse(response: DeleteResponse ) {
        Logger.debug(s"Finished deleting object ${response.status()}")
      }

      override def onFailure(exception: Exception) {
        Logger.error("Failure to delete.", exception)
      }
    })
  }
}

package services

import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import org.elasticsearch.node.NodeBuilder._
import org.elasticsearch.node.Node
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.common.xcontent.XContentFactory._
import java.util.Date
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.QueryBuilders
import models.Dataset
import scala.collection.mutable.ListBuffer
import models.Comment
import scala.util.parsing.json.JSONArray


/**
 * Elasticsearch plugin.
 *
 * @author Luigi Marini
 *
 */
class ElasticsearchPlugin(application: Application) extends Plugin {

  var node: Node = null
  var client: TransportClient = null

  override def onStart() {
    val configuration = application.configuration
    try {
      var nameOfCluster = play.api.Play.configuration.getString("elasticsearchSettings.clusterName").getOrElse("medici")
      var serverAddress = play.api.Play.configuration.getString("elasticsearchSettings.serverAddress").getOrElse("localhost")
      var serverPort = play.api.Play.configuration.getInt("elasticsearchSettings.serverPort").getOrElse(9300)
      
      node = nodeBuilder().clusterName(nameOfCluster).client(true).node()
      val settings = ImmutableSettings.settingsBuilder()
      settings.put("client.transport.sniff", true)
      settings.build();
      client = new TransportClient(settings)
      client.addTransportAddress(new InetSocketTransportAddress(serverAddress, serverPort))

      client.prepareIndex("data", "file")
      client.prepareIndex("data", "dataset")
      client.prepareIndex("data", "collection")
      
      Logger.info("ElasticsearchPlugin has started")
    } catch {
      case nn: NoNodeAvailableException => Logger.error("Error connecting to elasticsearch: " + nn)
      case _ : Throwable => Logger.error("Unknown exception connecting to elasticsearch")
    }
  }

  def search(index: String, query: String): SearchResponse = {
    Logger.info("Searching ElasticSearch for " + query)
    
    val response = client.prepareSearch(index)
      .setTypes("file","dataset","collection")
      .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
      //.setQuery(QueryBuilders.matchQuery("_all", query))
      .setQuery(QueryBuilders.queryString(query))
       .setFrom(0).setSize(60).setExplain(true)
      .execute()
      .actionGet()
      
    Logger.info("Search hits: " + response.getHits().getTotalHits())
    response
  }

  /**
   * Index document using an arbitrary map of fields.
   */
  def index(index: String, docType: String, id: String, fields: List[(String, String)]) {
    var builder = jsonBuilder()
      .startObject()
      fields.map(fv => builder.field(fv._1, fv._2))
      builder.endObject()
    val response = client.prepareIndex(index, docType, id)
      .setSource(builder)
      .execute()
      .actionGet()
    Logger.info("Indexing document: " + response.getId())
  }
  

  def delete(index: String, docType: String, id: String) {    
    val response = client.prepareDelete(index, docType, id)
      .execute()
      .actionGet()
    Logger.info("Deleting document: " + response.getId())
  }

  def indexDataset(dataset: Dataset) {
    Dataset.index(dataset.id.toString)
  }

  def testQuery() {
    val response = client.prepareSearch("twitter")
      .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
      .setQuery(termQuery("user", "kimchy"))
      .setFrom(0).setSize(60).setExplain(true)
      .execute()
      .actionGet();
    Logger.info(response.toString())
  }

  def testIndex() {
    val response = client.prepareIndex("twitter", "tweet", "1")
      .setSource(jsonBuilder()
        .startObject()
        .field("user", "kimchy")
        .field("postDate", new Date())
        .field("message", "trying out Elastic Search")
        .endObject())
      .execute()
      .actionGet();
    Logger.info(response.toString())
  }

  override def onStop() {
    client.close()
    node.close()
    Logger.info("ElasticsearchPlugin has stopped")
  }
}

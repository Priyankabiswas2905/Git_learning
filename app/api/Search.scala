package api

import services.{RdfSPARQLService, DatasetService, FileService, CollectionService, ElasticsearchPlugin, UserAccessRightsService}
import play.Logger
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions.mapAsScalaMap
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import javax.inject.{Inject, Singleton}
import play.api.Play.current
import play.api.Play.configuration
import models.UUID

@Singleton
class Search @Inject() (files: FileService, datasets: DatasetService, collections: CollectionService, sparql: RdfSPARQLService, accessRights: UserAccessRightsService)  extends ApiController {
  /**
   * Search results.
   */
  def search(query: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        Logger.debug("Searching for: " + query)

        var filesFound = ListBuffer.empty[models.File]
        var datasetsFound = ListBuffer.empty[models.Dataset]
        var collectionsFound = ListBuffer.empty[models.Collection]
        		
        var serviceConnected = true

        if (query != "") {

          val result = current.plugin[ElasticsearchPlugin].map { _.search("data", query.replaceAll("([:/\\\\])", "\\\\$1"))}
          
          result match {
            case Some(searchResponse) => {
            	try{
                    searchResponse.getHits() != null
                  }
                  catch{
                    case ex: NullPointerException =>{ //ElasticSearch not connected
                      serviceConnected = false
                    }
                  }
                  
                  serviceConnected match{
                    case false =>{	//ElasticSearch not connected              		
                    }
                    case true =>{	
		              for (hit <- searchResponse.getHits().getHits()) {
		                Logger.debug("Computing search result " + hit.getId())
		                Logger.info("Fields: ")
		                for ((key, value) <- mapAsScalaMap(hit.getFields())) {
		                  Logger.info(value.getName + " = " + value.getValue())
		                }
		                if (hit.getType() == "file") {
		                  files.get(UUID(hit.getId())) match {
		                    case Some(file) => {
		                      Logger.debug("FILES:hits.hits._id: Search result found file " + hit.getId());
		                      Logger.debug("FILES:hits.hits._source: Search result found dataset " + hit.getSource().get("datasetId"))
		                      //Logger.debug("Search result found file " + hit.getId()); files += file
		
		                      filesFound += file
		
		                    }
		                    case None => Logger.debug("File not found " + hit.getId())
		                   }
		                  } else if (hit.getType() == "dataset") {
		                    Logger.debug("DATASETS:hits.hits._source: Search result found dataset " + hit.getSource().get("name"))
		                    Logger.debug("DATASETS:Dataset.id=" + hit.getId());
		                    datasets.get(UUID(hit.getId())) match {
		                      case Some(dataset) =>
		                        Logger.debug("Search result found dataset" + hit.getId()); datasetsFound += dataset
		                      case None => {
		                        Logger.debug("Dataset not found " + hit.getId())
		                        //Redirect(routes.Datasets.dataset(hit.getId))
		                      }
		                    }
		                  }
		                else if (hit.getType() == "collection") {
		                  Logger.debug("COLLECTIONS:hits.hits._source: Search result found collection " + hit.getSource().get("name"))
		                  Logger.debug("COLLECTIONS:Collection.id=" + hit.getId())
		                  
		                  collections.get(UUID(hit.getId())) match {
		                    case Some(collection) =>
		                      Logger.debug("Search result found collection" + hit.getId()); collectionsFound += collection
		                    case None => {
		                      Logger.debug("Collection not found " + hit.getId())
		                    }
		                  }
		                  
		                }
		              }
		             }
                  }
              }
              case None => Logger.debug("Search returned no results")
              
            }
        }
        
        if(serviceConnected){
          val filesChecker = services.DI.injector.getInstance(classOf[api.Files])
          val datasetsChecker = services.DI.injector.getInstance(classOf[api.Datasets])
          val collectionsChecker = services.DI.injector.getInstance(classOf[api.Collections])
          
          request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	val filesJson = toJson(for(currFile <- filesFound.toList if(filesChecker.checkAccessForFileUsingRightsList(currFile, request.user , "view", rightsForUser))) yield {
		          currFile.id.stringify
		        } )
		        val datasetsJson = toJson(for(currDataset <- datasetsFound.toList if(datasetsChecker.checkAccessForDatasetUsingRightsList(currDataset, request.user , "view", rightsForUser))) yield {
		          currDataset.id.stringify
		        } )
		        val collectionsJson = toJson(for(currCollection <- collectionsFound.toList if(collectionsChecker.checkAccessForCollectionUsingRightsList(currCollection, request.user , "view", rightsForUser))) yield {
		          currCollection.id.stringify
		        } )
		        
		        val fullJSON = toJson(Map[String,JsValue]("files" -> filesJson, "datasets" -> datasetsJson, "collections" -> collectionsJson))
		        
		        Ok(fullJSON)
	        	
	        }
	        case None=>{
	        	val filesJson = toJson(for(currFile <- filesFound.toList if(filesChecker.checkAccessForFile(currFile, request.user, "view"))) yield {
		          currFile.id.stringify
		        } )
		        val datasetsJson = toJson(for(currDataset <- datasetsFound.toList if(datasetsChecker.checkAccessForDataset(currDataset, request.user, "view"))) yield {
		          currDataset.id.stringify
		        } )
		        val collectionsJson = toJson(for(currCollection <- collectionsFound.toList if(collectionsChecker.checkAccessForCollection(currCollection, request.user, "view"))) yield {
		          currCollection.id.stringify
		        } )
		        
		        val fullJSON = toJson(Map[String,JsValue]("files" -> filesJson, "datasets" -> datasetsJson, "collections" -> collectionsJson))
		        
		        Ok(fullJSON)
	        }
	      }
        }
        else{
            Ok("Text search service not connected.")
          } 
      }
     case None => {
    	 Logger.debug("Search plugin not enabled")
         Ok("Text search plugin not enabled.")
       }
    }
  }
  
  def querySPARQL() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDatasetsMetadata)) {
    implicit request =>

      configuration.getString("userdfSPARQLStore").getOrElse("no") match {
        case "yes" => {
          val queryText = request.body.asFormUrlEncoded.get("query").apply(0)
          Logger.info("whole msg: " + request.toString)
          val resultsString = sparql.sparqlQuery(queryText)
          Logger.info("SPARQL query results: " + resultsString)
          Ok(resultsString)
        }
        case _ => {
          Logger.error("RDF SPARQL store not used.")
          InternalServerError("Error searching RDF store. RDF SPARQL store not used.")
        }
      }
  }
}

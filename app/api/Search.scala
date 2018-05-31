package api

import services._
import play.Logger

import scala.collection.mutable.{HashMap, ListBuffer}
import scala.collection.JavaConversions.mapAsScalaMap
import edu.illinois.ncsa.isda.lsva.ImageMeasures
import edu.illinois.ncsa.isda.lsva.ImageDescriptors.FeatureType
import util.{SearchResult, SearchUtils}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.json.Json.toJson
import javax.inject.{Inject, Singleton}
import play.api.Play.current
import play.api.Play.configuration
import models._

@Singleton
class Search @Inject() (
   files: FileService,
   datasets: DatasetService,
   collections: CollectionService,
   previews: PreviewService,
   queries: MultimediaQueryService,
   sparql: RdfSPARQLService,
   indexService: IndexService)  extends ApiController {

  /** Search using a simple text string */
  def search(query: String) = PermissionAction(Permission.ViewDataset) { implicit request =>
    val response = indexService.search(query.replaceAll("([:/\\\\])", "\\\\$1"))

    val filesFound = response.filter(_.resourceType == ResourceRef.file).map(_.id.stringify)
    val datasetsFound = response.filter(_.resourceType == ResourceRef.dataset).map(_.id.stringify)
    val collectionsFound = response.filter(_.resourceType == ResourceRef.collection).map(_.id.stringify)

    Ok(toJson( Map[String,JsValue](
      "files" -> toJson(filesFound),
      "datasets" -> toJson(datasetsFound),
      "collections" -> toJson(collectionsFound)
    )))
  }

  /** Search using string-encoded Json object (e.g. built by Advanced Search form) */
  def searchJson(query: String, grouping: String, from: Option[Int], size: Option[Int]) = PermissionAction(Permission.ViewDataset) {
    implicit request =>
      implicit val user = request.user

      val response = indexService.search(query.replaceAll("([:/\\\\])", "\\\\$1"))

      val filesFound = response.filter(_.resourceType == ResourceRef.file).flatMap(x => files.get(x.id))
      val datasetsFound = response.filter(_.resourceType == ResourceRef.dataset).flatMap(x => datasets.get(x.id))
      val collectionsFound = response.filter(_.resourceType == ResourceRef.collection).flatMap(x => collections.get(x.id))

      // Use "distinct" to remove duplicate results.
      Ok(JsObject(Seq(
        "datasets" -> toJson(datasetsFound.distinct),
        "files" -> toJson(filesFound.distinct),
        "collections" -> toJson(collectionsFound.distinct),
        "count" -> toJson(response.length)
      )))
  }

  def querySPARQL() = PermissionAction(Permission.ViewMetadata) { implicit request =>
      configuration.getString("userdfSPARQLStore").getOrElse("no") match {
        case "yes" => {
          val queryText = request.body.asFormUrlEncoded.get("query").apply(0)
          Logger.debug("whole msg: " + request.toString)
          val resultsString = sparql.sparqlQuery(queryText)
          Logger.debug("SPARQL query results: " + resultsString)
          Ok(resultsString)
        }
        case _ => {
          Logger.error("RDF SPARQL store not used.")
          InternalServerError("Error searching RDF store. RDF SPARQL store not used.")
        }
      }
  }

  /**
   * Search MultimediaFeatures.
   */
  def searchMultimediaIndex(section_id: UUID) = PermissionAction(Permission.ViewSection,
    Some(ResourceRef(ResourceRef.section, section_id))) {
    implicit request =>

    // Finding IDs of spaces that the user has access to
    val user = request.user
    val spaceIDsList = user.get.spaceandrole.map(_.spaceId)
    Logger.debug("Searching multimedia index " + section_id.stringify)

    // TODO handle multiple previews found
    val preview = previews.findBySectionId(section_id)(0)
    queries.findFeatureBySection(section_id) match {

      case Some(feature) => {
        // setup priority queues
        val queues = new HashMap[String, List[MultimediaDistance]]
        val representations = feature.features.map { f =>
          queues(f.representation) = queries.searchMultimediaDistances(section_id.toString,f.representation,20, spaceIDsList)
        }

        val items = new HashMap[String, ListBuffer[SearchResult]]
        queues map {
          case (key, queue) =>
            val list = new ListBuffer[SearchResult]
            for (element <- queue) {              
              val previewsBySection = previews.findBySectionId(element.target_section)
              if (previewsBySection.size == 1) {
                Logger.trace("Appended search result " + key + " " + element.target_section + " " + element.distance + " " + previewsBySection(0).id.toString)
                list.append(SearchResult(element.target_section.toString, element.distance, Some(previewsBySection(0).id.toString)))
              } else {
                Logger.error("Found more/less than one preview " + preview)
              }
            }
            items += key -> list
        }

        val jsonResults = toJson(items.toMap)
        Ok(jsonResults)
                
      }
      case None => InternalServerError("feature not found")
    }
  }
}

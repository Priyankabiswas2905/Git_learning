package api

import java.net.URL
import java.util.Date
import javax.inject.{Inject, Singleton}

import com.wordnik.swagger.annotations.ApiOperation
import models.{ResourceRef, UUID, UserAgent, _}
import org.elasticsearch.action.search.SearchResponse
import play.api.Logger
import play.api.Play._
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.Result
import services._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
 * Manipulate generic metadata.
 */
@Singleton
class Metadata @Inject()(
  metadataService: MetadataService,
  contextService: ContextLDService,
  userService: UserService,
  datasets: DatasetService,
  files: FileService,
  curations:CurationService,
  events: EventService,
  spaceService: SpaceService) extends ApiController {
  
  def search() = PermissionAction(Permission.ViewDataset)(parse.json) { implicit request =>
    Logger.debug("Searching metadata")
    val results = metadataService.search(request.body)
    Ok(toJson(results))
  }

  def searchByKeyValue(key: Option[String], value: Option[String], extractorName: Option[String], count: Int = 0) = PermissionAction(Permission.ViewDataset) {
      implicit request =>
        implicit val user = request.user

        val response = for {
          k <- key
          v <- value
          e <- extractorName
        } yield {
          val results = metadataService.search(k, v, e, count, user)
          val datasetsResults = results.flatMap { d =>
            if (d.resourceType == ResourceRef.dataset) datasets.get(d.id) else None
          }
          val filesResults = results.flatMap { f =>
            if (f.resourceType == ResourceRef.file) files.get(f.id) else None
          }
          import Dataset.datasetWrites
          import File.FileWrites
          // Use "distinct" to remove duplicate results.
          Ok(JsObject(Seq("datasets" -> toJson(datasetsResults.distinct), "files" -> toJson(filesResults.distinct))))
        }
        response getOrElse BadRequest(toJson("You must specify key and value"))
  }

  def getDefinitions() = PermissionAction(Permission.ViewDataset) {
    implicit request =>
      val vocabularies = metadataService.getDefinitions()
      Ok(toJson(vocabularies))
  }

  def getDefinitionsDistinctName() = PermissionAction(Permission.ViewDataset) {
    implicit request =>
      implicit val user = request.user
      val vocabularies = metadataService.getDefinitionsDistinctName(user)
      Ok(toJson(vocabularies))
  }

  /**
    * Flatten json object to have a single level of key/values with dot notation for nesting
    */
  def flattenJson(js: JsValue, prefix: String = ""): JsObject = {
    // From http://stackoverflow.com/questions/24273433/play-scala-how-to-flatten-a-json-object

    // We will use this substring to trim extractor names from key strings
    //    e.g. "http://localhost:9000/clowder/api/extractors/wordCount.lines" -> "wordCount.lines"
    val extractorString = "/extractors/"

    js.as[JsObject].fields.foldLeft(Json.obj()) {
      // value is sub-object so recursively handle
      case (acc, (k, v: JsObject)) => {
        val key = if (k contains extractorString) {
          k.substring(k.indexOf(extractorString)+extractorString.length())
        } else k

        if(prefix.isEmpty) acc.deepMerge(flattenJson(v, key))
        else acc.deepMerge(flattenJson(v, s"$prefix.$key"))
      }
      case (acc, (k, v)) => {
        val key = if (k contains extractorString) {
          k.substring(k.indexOf(extractorString)+extractorString.length())
        } else k

        if(prefix.isEmpty) acc + (key -> v)
        else acc + (s"$prefix.$key" -> v)
      }
    }
  }

  /**
    * Get set of metadata fields containing filter substring for autocomplete - uses ElasticSearch
    */
  def getAutocompleteName(filter: String) = PermissionAction(Permission.ViewDataset) { implicit request =>
    implicit val user = request.user

    var listOfTerms = ListBuffer.empty[String]

    // First, get regular vocabulary matches
    val vocabularies = metadataService.getDefinitionsDistinctName(user)
    for (md_def <- vocabularies) {
      val currVal = (md_def.json \ "label").as[String]
      if (currVal contains filter) {
        listOfTerms.append(currVal)
      }
    }

    // Next get ElasticSeach metadata fields if plugin available
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        Logger.debug("Getting autocomplete suggestions for: " + filter)

        if (filter != "") {
          val result: Option[SearchResponse] = current.plugin[ElasticsearchPlugin].map {
            _.search("data", "*"+filter.replaceAll("([+:/\\\\])", "\\\\$1")+"*")
          }

          // PARSE RESPONSES
          result match {
            case Some(searchResponse) => {
              for (hit <- searchResponse.getHits().getHits()) {
                hit.getSource.get("metadata") match {
                  case md: String => {
                    val json: JsValue = Json.parse(md)
                    val flat = flattenJson(json)

                    val fieldList: Seq[(String,JsValue)] = flat.as[JsObject].fields
                    fieldList.foreach[Unit]( (tuple: Tuple2[String,JsValue]) => {
                      // Check key of metadata for the currently targeted search strong
                      if ((tuple._1 contains filter) && !(listOfTerms contains tuple._1)) {
                        listOfTerms += tuple._1
                      }
                    })
                  }
                  case md: JsValue => {
                    val flat = flattenJson(md)
                    val fieldList: Seq[(String,JsValue)] = flat.as[JsObject].fields
                    fieldList.foreach[Unit]( (tuple: Tuple2[String,JsValue]) => {
                      // Check key of metadata for the currently targeted search strong
                      if ((tuple._1 contains filter) && !(listOfTerms contains tuple._1)) {
                        listOfTerms += tuple._1
                      }
                    })
                  }
                  case _ => {
                    Logger.debug("Encountered unexpected ElasticSearch hit type")
                  }
                  case None => Logger.debug("No metadata hits found from ElasticSearch")
                }
              }
            }
            case None => {
              Logger.debug("Search returned no results")
            }
          }
        }
        Ok(toJson(listOfTerms))
      }
      case None => {
        Logger.debug("Search plugin not enabled")
        Ok
      }
    }
  }

  def getDefinitionsByDataset(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        val metadataDefinitions = collection.mutable.HashSet[models.MetadataDefinition]()
        dataset.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(space) => metadataService.getDefinitions(Some(space.id)).foreach{definition => metadataDefinitions += definition}
            case None =>
          }
        }
        if(dataset.spaces.length == 0) {
          metadataService.getDefinitions().foreach{definition => metadataDefinitions += definition}
        }
        Ok(toJson(metadataDefinitions.toList))
      }
      case None => BadRequest(toJson("The request dataset does not exist"))
    }
  }

  def getDefinition(id: UUID) = PermissionAction(Permission.AddMetadata).async { implicit request =>
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val foo = for {
      md <- metadataService.getDefinition(id)
      url <- (md.json \ "definitions_url").asOpt[String]
    } yield {
      WS.url(url).get().map(response => Ok(response.body.trim))
    }
    foo.getOrElse {
      Future(InternalServerError)
    }
  }

  def getUrl(inUrl: String) = PermissionAction(Permission.AddMetadata).async { implicit request =>
    // Use java.net.URI instead of URLDecoder.decode to decode the path.
    import java.net.URI
    Logger.debug("Metadata getUrl: inUrl = '" + inUrl + "'.")
    // Replace " " with "+", otherwise the decoded URL might contain spaces and break Ws.url(url).
    val url = new URI(inUrl).getPath().replaceAll(" ", "+")
    Logger.debug("Metadata getUrl decoded: url = '" + url + "'.")
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    WS.url(url).get().map {
      response => Ok(response.body.trim)
    }
  }

  def addDefinitionToSpace(spaceId: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId))) (parse.json){ implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        val body = request.body
        if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined && (body \ "uri").asOpt[String].isDefined) {
          val uri = (body \ "uri").as[String]
          spaceService.get(spaceId) match {
            case Some(space) => {
              addDefinitionHelper(uri, body, Some(space.id), u, Some(space))
            }
            case None => BadRequest("The space does not exist")
          }
        } else {
          BadRequest(toJson("Invalid resource Type"))
        }
      }
      case None => BadRequest(toJson("Invalid user"))
    }
  }

  def addDefinition() = ServerAdminAction(parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val body = request.body
          if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined && (body \ "uri").asOpt[String].isDefined) {
            val uri = (body \ "uri").as[String]
            addDefinitionHelper(uri, body, None, user, None)
          } else {
            BadRequest(toJson("Invalid Resource type"))
          }
        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  def addDefinitionHelper(uri: String, body: JsValue, spaceId: Option[UUID], user: User, space: Option[ProjectSpace]): Result = {
    metadataService.getDefinitionByUri(uri) match {
      case Some(metadata) => BadRequest(toJson("Metadata definition with same uri exists."))
      case None => {
        val definition = MetadataDefinition(json = body, spaceId = spaceId)
        metadataService.addDefinition(definition)
        space match {
          case Some(s) => {
            events.addObjectEvent(Some(user), s.id, s.name, "added_metadata_space")
          }
          case None => {
            events.addEvent(new Event(user.getMiniUser, None, None, None, None, None, "added_metadata_instance", new Date()))
          }
        }
        Ok(JsObject(Seq("status" -> JsString("ok"))))
      }
    }
  }

  def editDefinition(id:UUID, spaceId: Option[String]) = ServerAdminAction (parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val body = request.body
          if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined && (body \ "uri").asOpt[String].isDefined) {
            val uri = (body \ "uri").as[String]
            metadataService.getDefinitionByUriAndSpace(uri, spaceId) match {
              case Some(metadata)  => if( metadata.id != id) {
                BadRequest(toJson("Metadata definition with same uri exists."))
              } else {
                metadataService.editDefinition(id, body)
                metadata.spaceId match {
                  case Some(spaceId) => {
                    spaceService.get(spaceId) match {
                      case Some(s) => events.addObjectEvent(Some(user), s.id, s.name, "edit_metadata_space")
                      case None =>
                    }
                  }
                  case None => {
                    events.addEvent(new Event(user.getMiniUser, None, None, None, None, None, "edit_metadata_instance", new Date()))
                  }
                }
                Ok(JsObject(Seq("status" -> JsString("ok"))))
              }
              case None => {
                metadataService.editDefinition(id, body)
                Ok(JsObject(Seq("status" -> JsString("ok"))))
              }
            }
          } else {
            BadRequest(toJson("Invalid resource type"))
          }

        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  def deleteDefinition(id: UUID) = ServerAdminAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(user) => {
        metadataService.getDefinition(id) match {
          case Some(md) => {
            metadataService.deleteDefinition(id)

            md.spaceId match {
              case Some(spaceId) => {
                spaceService.get(spaceId) match {
                  case Some(s) => events.addObjectEvent(Some(user), s.id, s.name, "delete_metadata_space")
                  case None =>
                }
              }
              case None => {
                events.addEvent(new Event(user.getMiniUser, None, None, None, None, None, "delete_metadata_instance", new Date()))
              }
            }
            Ok(JsObject(Seq("status" -> JsString("ok"))))
          }
          case None => BadRequest(toJson("Invalid metadata definition"))
          }

      }
      case None => BadRequest(toJson("Invalid user"))
    }
  }

  def addUserMetadata() = PermissionAction(Permission.AddMetadata)(parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val json = request.body
          // when the new metadata is added
          val createdAt = new Date()

          // build creator uri
          // TODO switch to internal id and then build url when returning?
          val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + user.id
          val creator = UserAgent(user.id, "cat:user", MiniUser(user.id, user.fullName, user.avatarUrl.getOrElse(""), user.email), Some(new URL(userURI)))

          val context: JsValue = (json \ "@context")

          // figure out what resource this is attached to
          val attachedTo =
            if ((json \ "file_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.file, UUID((json \ "file_id").as[String])))
            else if ((json \ "dataset_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.dataset, UUID((json \ "dataset_id").as[String])))
            else if ((json \ "curationObject_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.curationObject, UUID((json \ "curationObject_id").as[String])))
            else if ((json \ "curationFile_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.curationFile, UUID((json \ "curationFile_id").as[String])))
            else None

          // check if the context is a URL to external endpoint
          val contextURL: Option[URL] = context.asOpt[String].map(new URL(_))

          // check if context is a JSON-LD document
          val contextID: Option[UUID] =
            if (context.isInstanceOf[JsObject]) {
              context.asOpt[JsObject].map(contextService.addContext(new JsString("context name"), _))
            } else if (context.isInstanceOf[JsArray]) {
              context.asOpt[JsArray].map(contextService.addContext(new JsString("context name"), _))
            } else None

          //parse the rest of the request to create a new models.Metadata object
          val content = (json \ "content")
          val version = None

          if (attachedTo.isDefined) {
            val metadata = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creator,
              content, version)

            //add metadata to mongo
            metadataService.addMetadata(metadata)

            Ok(views.html.metadatald.view(List(metadata), true)(request.user))
          } else {
            BadRequest(toJson("Invalid resource type"))
          }
        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  @ApiOperation(value = "Delete the metadata represented in Json-ld format",
    responseClass = "None", httpMethod = "DELETE")
  def removeMetadata(id:UUID) = PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(ResourceRef.metadata, id))) { implicit request =>
    request.user match {
      case Some(user) => {
        metadataService.getMetadataById(id) match {
          case Some(m) => {
            if(m.attachedTo.resourceType == ResourceRef.curationObject && curations.get(m.attachedTo.id).map(_.status != "In Curation").getOrElse(false)
            || m.attachedTo.resourceType == ResourceRef.curationFile && curations.getCurationByCurationFile(m.attachedTo.id).map(_.status != "In Curation").getOrElse(false)) {
              BadRequest("Curation Object has already submitted")
            } else {
              metadataService.removeMetadata(id)
              Ok(JsObject(Seq("status" -> JsString("ok"))))
            }
          }
          case None => BadRequest(toJson("Invalid Metadata"))
        }
      }
      case None => BadRequest("Not authorized.")
    }
  }
}

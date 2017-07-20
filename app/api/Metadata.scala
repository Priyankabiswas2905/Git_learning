package api

import java.net.{ URL, URLEncoder }
import java.util.Date
import javax.inject.{ Inject, Singleton }

import models.{ ResourceRef, UUID, UserAgent, _ }
import org.elasticsearch.action.search.SearchResponse
import org.apache.commons.lang.WordUtils
import play.api.Play.current
import play.api.Logger
import play.api.Play._
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.Result
import services._
import play.api.i18n.Messages

import play.api.libs.json.JsValue

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
 * Manipulate generic metadata.
 */
@Singleton
class Metadata @Inject() (
    metadataService: MetadataService,
    contextService: ContextLDService,
    userService: UserService,
    datasets: DatasetService,
    files: FileService,
    curations: CurationService,
    events: EventService,
    spaceService: SpaceService) extends ApiController {

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

  /** Get set of metadata fields containing filter substring for autocomplete */
  def getAutocompleteName(query: String) = PermissionAction(Permission.ViewDataset) { implicit request =>
    implicit val user = request.user

    var listOfTerms = ListBuffer.empty[String]

    // First, get regular vocabulary matches
    val definitions = metadataService.getDefinitionsDistinctName(user)
    for (md_def <- definitions) {
      val currVal = (md_def.json \ "label").as[String]
      if (currVal.toLowerCase startsWith query.toLowerCase) {
        listOfTerms.append("metadata." + currVal)
      }
    }

    // Next get Elasticsearch metadata fields if plugin available
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        val mdTerms = plugin.getAutocompleteMetadataFields(query)
        for (term <- mdTerms) {
          // e.g. "metadata.http://localhost:9000/clowder/api/extractors/terra.plantcv.angle",
          //      "metadata.Jane Doe.Alternative Title"
          if (!(listOfTerms contains term))
            listOfTerms.append(term)
        }
        Ok(toJson(listOfTerms.distinct))
      }
      case None => {
        BadRequest("Elasticsearch plugin is not enabled")
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
            case Some(space) => metadataService.getDefinitions(Some(space.id)).foreach { definition => metadataDefinitions += definition }
            case None =>
          }
        }
        if (dataset.spaces.length == 0) {
          metadataService.getDefinitions().foreach { definition => metadataDefinitions += definition }
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

  def addDefinitionToSpace(spaceId: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId)))(parse.json) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        var body = request.body
        if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined) {
          var uri = (body \ "uri").asOpt[String].getOrElse("")
          spaceService.get(spaceId) match {
            case Some(space) => {
              // assign a default uri if not specified
              if (uri == "") {
                // http://clowder.ncsa.illinois.edu/metadata/{uuid}#CamelCase
                uri = play.Play.application().configuration().getString("metadata.uri.prefix") + "/" + space.id.stringify + "#" + WordUtils.capitalize((body \ "label").as[String]).replaceAll("\\s", "")
                body = body.as[JsObject] + ("uri" -> Json.toJson(uri))
              }
              addDefinitionHelper(uri, (body \ "label").as[String], body, Some(space.id), u, Some(space))
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
          var body = request.body
          if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined) {
            var uri = (body \ "uri").asOpt[String].getOrElse("")
            // assign a default uri if not specified
            if (uri == "") {
              // http://clowder.ncsa.illinois.edu/metadata#CamelCase
              uri = play.Play.application().configuration().getString("metadata.uri.prefix") + "#" + WordUtils.capitalize((body \ "label").as[String]).replaceAll("\\s", "")
              body = body.as[JsObject] + ("uri" -> Json.toJson(uri))
            }
            addDefinitionHelper(uri, (body \ "label").as[String], body, None, user, None)
          } else {
            BadRequest(toJson("Invalid Resource type"))
          }
        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  //On GUI, URI is not required, however URI is required in DB. a default one will be generated when needed.
  private def addDefinitionHelper(uri: String, label: String, body: JsValue, spaceId: Option[UUID], user: User, space: Option[ProjectSpace]): Result = {
    metadataService.getDefinitionByUriAndSpace(uri, space map { _.id.toString() }) match {
      case Some(metadata) => BadRequest(toJson("Metadata definition with same uri exists."))
      case None => {
        metadataService.getDefinitionByLabelAndSpace(label, space map { _.id.toString() }) match {
          case Some(metadata) => BadRequest(toJson("Metadata definition with same label exists."))
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
    }
  }

  def editDefinition(id: UUID, spaceId: Option[String]) = ServerAdminAction(parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val body = request.body
          if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined && (body \ "uri").asOpt[String].isDefined) {
            val uri = (body \ "uri").as[String]
            metadataService.getDefinitionByUriAndSpace(uri, spaceId) match {
              case Some(metadata) => if (metadata.id != id) {
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

  def makeDefinitionAddable(id: UUID, addable: Boolean) = ServerAdminAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(user) => {
        metadataService.getDefinition(id) match {
          case Some(md) => {
            metadataService.makeDefinitionAddable(id, addable)

            md.spaceId match {
              case Some(spaceId) => {
                spaceService.get(spaceId) match {
                  case Some(s) => events.addObjectEvent(Some(user), s.id, s.name, "toggle_metadata_space")
                  case None =>
                }
              }
              case None => {
                events.addEvent(new Event(user.getMiniUser, None, None, None, None, None, "toggle_metadata_instance", new Date()))
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
          val (attachedTo, space) =
            if ((json \ "file_id").asOpt[String].isDefined) {
              files.get(UUID((json \ "file_id").as[String])) match {
                case Some(file) => {

                  (Some(ResourceRef(ResourceRef.file, UUID((json \ "file_id").as[String]))), None)
                }
              }
            } else if ((json \ "dataset_id").asOpt[String].isDefined) {
              datasets.get(UUID((json \ "dataset_id").as[String])) match {
                case Some(dataset) => {
                  (Some(ResourceRef(ResourceRef.dataset, UUID((json \ "dataset_id").as[String]))), Some(dataset.spaces.apply(0)))
                }
              }
            } else if ((json \ "curationObject_id").asOpt[String].isDefined) {
              (Some(ResourceRef(ResourceRef.curationObject, UUID((json \ "curationObject_id").as[String]))), None)
            } else if ((json \ "curationFile_id").asOpt[String].isDefined) {
              (Some(ResourceRef(ResourceRef.curationFile, UUID((json \ "curationFile_id").as[String]))), None)
            } else (None, None)

          val content = (json \ "content")
          val content_ld = (json \ "content_ld")

          if (attachedTo.isDefined) {

            content match {
              case c: JsObject => {

                //parse the rest of the request to create a new models.Metadata object

                // check if the context is a URL to external endpoint
                val contextURL: Option[URL] = context.asOpt[String].map(new URL(_))

                // check if context is a JSON-LD document
                val contextID: Option[UUID] =
                  if (context.isInstanceOf[JsObject]) {
                    context.asOpt[JsObject].map(contextService.addContext(new JsString("context name"), _))
                  } else if (context.isInstanceOf[JsArray]) {
                    context.asOpt[JsArray].map(contextService.addContext(new JsString("context name"), _))
                  } else None

                val version = None
                val metadata = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creator,
                  content, version)

                //add metadata to mongo
                metadataService.addMetadata(metadata)
                val mdMap = metadata.getExtractionSummary

                attachedTo match {
                  case Some(resource) => {
                    resource.resourceType match {
                      case ResourceRef.dataset => {
                        datasets.index(resource.id)
                        //send RabbitMQ message
                        current.plugin[RabbitmqPlugin].foreach { p =>
                          val dtkey = s"${p.exchange}.metadata.added"
                          p.extract(ExtractorMessage(UUID(""), UUID(""), controllers.Utils.baseEventUrl(request),
                            dtkey, mdMap, "", metadata.attachedTo.id, ""))
                        }
                      }
                      case ResourceRef.file => {
                        files.index(resource.id)
                        //send RabbitMQ message
                        current.plugin[RabbitmqPlugin].foreach { p =>
                          val dtkey = s"${p.exchange}.metadata.added"
                          p.extract(ExtractorMessage(metadata.attachedTo.id, UUID(""), controllers.Utils.baseEventUrl(request),
                            dtkey, mdMap, "", UUID(""), ""))
                        }
                      }
                      case _ => {}
                    }
                  }

                }
              }
              case u: JsUndefined => {
                if (content_ld.isInstanceOf[JsUndefined]) {
                  BadRequest(toJson("No metadata entries"))
                }
              }
            }

            content_ld match {
              case c: JsObject => {

                //add metadata to mongo
                val newInfo = metadataService.addMetadata(content_ld, context, attachedTo.get, createdAt, creator, space)
                Logger.info("new stuff is: " + newInfo.toString())
                val mdMap = Map("metadata" -> content,
                  "resourceType" -> attachedTo.get.resourceType.name,
                  "resourceId" -> attachedTo.get.id.toString)

                attachedTo match {
                  case Some(resource) => {
                    resource.resourceType match {
                      case ResourceRef.dataset => {
                        datasets.index(resource.id)
                        //send RabbitMQ message
                        current.plugin[RabbitmqPlugin].foreach { p =>
                          val dtkey = s"${p.exchange}.metadata.added"
                          p.extract(ExtractorMessage(UUID(""), UUID(""), controllers.Utils.baseEventUrl(request),
                            dtkey, mdMap, "", attachedTo.get.id, ""))
                        }
                      }
                      case ResourceRef.file => {
                        files.index(resource.id)
                        //send RabbitMQ message
                        current.plugin[RabbitmqPlugin].foreach { p =>
                          val dtkey = s"${p.exchange}.metadata.added"
                          p.extract(ExtractorMessage(attachedTo.get.id, UUID(""), controllers.Utils.baseEventUrl(request),
                            dtkey, mdMap, "", UUID(""), ""))
                        }
                      }
                      case _ => {}
                    }
                  }
                  case None => {}
                }

                Ok(JsObject(Seq("status" -> JsString("ok"))) ++ newInfo)
              }
              case _ => {
                Ok(JsObject(Seq("status" -> JsString("ok"))))
              }
            }

            /*           
val metadata = models.Metadata(UUID.generate, attachedTo.get, None, Some(new URL("http://noteal.com")), createdAt, creator,
                  content, None)
            Ok(views.html.metadatald.view(List(metadata), null, true)(request.user))
            * 
            */

          } else {
            BadRequest(toJson("Invalid resource type"))
          }
        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  def updateMetadata(attachedtype: String, attachedid: UUID, entryId: String) = PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(Symbol(attachedtype), attachedid)))(parse.json) { implicit request =>
    request.user match {
      case Some(user) => {
        if (attachedtype == ResourceRef.curationObject.name && curations.get(attachedid).map(_.status != "In Preparation").getOrElse(false)
          || attachedtype == ResourceRef.curationFile.name && curations.getCurationByCurationFile(attachedid).map(_.status != "In Preparation").getOrElse(false)) {
          BadRequest("Publication Request has already been submitted")
        } else {

          val updatedAt = new Date()

          // build creator uri
          // TODO switch to internal id and then build url when returning?
          val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + user.id
          val updator = UserAgent(user.id, "cat:user", MiniUser(user.id, user.fullName, user.avatarUrl.getOrElse(""), user.email), Some(new URL(userURI)))
          val space = attachedtype match {
            case ResourceRef.file.name => {
              files.get(attachedid) match {
                case Some(file) => {
                  None
                }
              }
            }
            case ResourceRef.dataset.name => {
              datasets.get(attachedid) match {
                case Some(dataset) => {
                  Some(dataset.spaces.apply(0))
                }
              }
            }
            case ResourceRef.curationObject.name => { None }
            case ResourceRef.curationFile.name => { None }
            case _ => None
          }

          val content = (request.body \ "content_ld")
          val context = (request.body \ "@context")

          metadataService.updateMetadata(content, context, ResourceRef(Symbol(attachedtype), attachedid), entryId, updatedAt, updator, space) match {
            case u: JsUndefined => {
              BadRequest("Entry to update not found.")
            }
            case result: JsObject => {

              val mdMap = Map("metadata" -> content,
                "resourceType" -> attachedtype,
                "resourceId" -> attachedid)

              current.plugin[RabbitmqPlugin].foreach { p =>
                val dtkey = s"${p.exchange}.metadata.updated"
                p.extract(ExtractorMessage(UUID(""), UUID(""), request.host, dtkey, mdMap, "", attachedid, ""))
              }

              Logger.debug("re-indexing after metadata update")
              current.plugin[ElasticsearchPlugin].foreach { p =>
                // Delete existing index entry and re-index
                Symbol(attachedtype) match {
                  case ResourceRef.file => {
                    p.delete("data", "file", attachedid.stringify)
                    files.index(attachedid)
                  }
                  case ResourceRef.dataset => {
                    p.delete("data", "dataset", attachedid.stringify)
                    datasets.index(attachedid)
                  }
                  case _ => {
                    Logger.error("unknown attached resource type for metadata - not reindexing")
                  }
                }
              }

              Ok(JsObject(Seq("status" -> JsString("ok"))) ++ result)
            }

          }
        }
      }
      case None => BadRequest("Not authorized.")
    }
  }

  def removeMetadataById(id: UUID) = PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(ResourceRef.metadata, id))) { implicit request =>
    request.user match {
      case Some(user) => {
        metadataService.getMetadataById(id) match {
          case Some(m) => {
            if (m.attachedTo.resourceType == ResourceRef.curationObject && curations.get(m.attachedTo.id).map(_.status != "In Preparation").getOrElse(false)
              || m.attachedTo.resourceType == ResourceRef.curationFile && curations.getCurationByCurationFile(m.attachedTo.id).map(_.status != "In Preparation").getOrElse(false)) {
              BadRequest("Publication Request has already been submitted")
            } else {
              metadataService.removeMetadataById(id)
              val mdMap = m.getExtractionSummary

              current.plugin[RabbitmqPlugin].foreach { p =>
                val dtkey = s"${p.exchange}.metadata.removed"
                p.extract(ExtractorMessage(UUID(""), UUID(""), request.host, dtkey, mdMap, "", id, ""))
              }

              Logger.debug("re-indexing after metadata removal")
              current.plugin[ElasticsearchPlugin].foreach { p =>
                // Delete existing index entry and re-index
                m.attachedTo.resourceType match {
                  case ResourceRef.file => {
                    p.delete("data", "file", m.attachedTo.id.stringify)
                    files.index(m.attachedTo.id)
                  }
                  case ResourceRef.dataset => {
                    p.delete("data", "dataset", m.attachedTo.id.stringify)
                    datasets.index(m.attachedTo.id)
                  }
                  case _ => {
                    Logger.error("unknown attached resource type for metadata - not reindexing")
                  }
                }
              }

              Ok(JsObject(Seq("status" -> JsString("ok"))))
            }
          }
          case None => BadRequest(toJson("Invalid Metadata"))
        }
      }
      case None => BadRequest("Not authorized.")
    }
  }

  def removeMetadata(attachedtype: String, attachedid: String, term: String, itemid: String) = PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(Symbol(attachedtype), UUID(attachedid)))) { implicit request =>
    val attachedUuid = UUID(attachedid)
    request.user match {
      case Some(user) => {

        if (attachedtype == ResourceRef.curationObject.name && curations.get(attachedUuid).map(_.status != "In Preparation").getOrElse(false)
          || attachedtype == ResourceRef.curationFile.name && curations.getCurationByCurationFile(attachedUuid).map(_.status != "In Preparation").getOrElse(false)) {
          BadRequest("Publication Request has already been submitted")
        } else {

          val deletedAt = new Date()

          // build creator uri
          // TODO switch to internal id and then build url when returning?
          val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + user.id
          val deletor = UserAgent(user.id, "cat:user", MiniUser(user.id, user.fullName, user.avatarUrl.getOrElse(""), user.email), Some(new URL(userURI)))
          val space = attachedtype match {
            case ResourceRef.file.name => {
              files.get(attachedUuid) match {
                case Some(file) => {
                  None
                }
              }
            }
            case ResourceRef.dataset.name => {
              datasets.get(attachedUuid) match {
                case Some(dataset) => {
                  Some(dataset.spaces.apply(0))
                }
              }
            }
            case ResourceRef.curationObject.name => { None }
            case ResourceRef.curationFile.name => { None }
            case _ => None
          }

          metadataService.removeMetadata(ResourceRef(Symbol(attachedtype), attachedUuid), term, itemid, deletedAt, deletor, space) match {
            case content: JsObject => {

              val mdMap = Map("metadata" -> content,
                "resourceType" -> attachedtype,
                "resourceId" -> attachedUuid)

              current.plugin[RabbitmqPlugin].foreach { p =>
                val dtkey = s"${p.exchange}.metadata.removed"
                p.extract(ExtractorMessage(UUID(""), UUID(""), request.host, dtkey, mdMap, "", attachedUuid, ""))
              }

              Logger.debug("re-indexing after metadata removal")
              current.plugin[ElasticsearchPlugin].foreach { p =>
                // Delete existing index entry and re-index
                Symbol(attachedtype) match {
                  case ResourceRef.file => {
                    p.delete("data", "file", attachedUuid.stringify)
                    files.index(attachedUuid)
                  }
                  case ResourceRef.dataset => {
                    p.delete("data", "dataset", attachedUuid.stringify)
                    datasets.index(attachedUuid)
                  }
                  case _ => {
                    Logger.error("unknown attached resource type for metadata - not reindexing")
                  }
                }
              }

              Ok(JsObject(Seq("status" -> JsString("ok"))))
            }
            case u: JsUndefined => {
              BadRequest("Entry to delete not found.")
            }
          }
        }
      }
      case None => BadRequest("Not authorized.")
    }
  }

  def getPerson(pid: String) = PermissionAction(Permission.ViewMetadata).async { implicit request =>

    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val peopleEndpoint = (play.Play.application().configuration().getString("people.uri"))
    if (peopleEndpoint != null) {
      val endpoint = (peopleEndpoint + "/" + URLEncoder.encode(pid, "UTF-8"))
      val futureResponse = WS.url(endpoint).get()
      var jsonResponse: play.api.libs.json.JsValue = new JsArray()
      var success = false
      val result = futureResponse.map {
        case response =>
          if (response.status >= 200 && response.status < 300 || response.status == 304) {
            Ok(response.json).as("application/json")
          } else {
            if (response.status == 404) {

              NotFound(toJson(Map("failure" -> { "Person with identifier " + pid + " not found" }))).as("application/json")

            } else {
              InternalServerError(toJson(Map("failure" -> { "Status: " + response.status.toString() + " returned from SEAD /api/people/<id> service" }))).as("application/json")
            }
          }
      }
      result
    } else {
      //TBD - see what Clowder knows
      Future(NotFound(toJson(Map("failure" -> { "Person with identifier " + pid + " not found" }))).as("application/json"))
    }
  }

  def listPeople(term: String, limit: Int) = PermissionAction(Permission.ViewMetadata).async { implicit request =>

    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val endpoint = (play.Play.application().configuration().getString("people.uri"))
    if (play.api.Play.current.plugin[services.StagingAreaPlugin].isDefined && endpoint != null) {

      val futureResponse = WS.url(endpoint).get()
      var jsonResponse: play.api.libs.json.JsValue = new JsArray()
      var success = false
      val lcTerm = term.toLowerCase()
      val result = futureResponse.map {
        case response =>
          if (response.status >= 200 && response.status < 300 || response.status == 304) {
            val people = (response.json \ ("persons")).as[List[JsObject]]
            Ok(Json.toJson(people.map { t =>
              val fName = t \ ("givenName")
              val lName = t \ ("familyName")
              val name = JsString(fName.as[String] + " " + lName.as[String])
              val email = t \ ("email") match {
                case JsString(_) => t \ ("email")
                case _ => JsString("")
              }
              Map("name" -> name, "@id" -> t \ ("@id"), "email" -> email)

            }.filter((x) => {
              if (term.length == 0) {
                true
              } else {
                Logger.debug(lcTerm)

                ((x.getOrElse("name", new JsString("")).as[String].toLowerCase().contains(lcTerm)) ||
                  x.getOrElse("@id", new JsString("")).as[String].toLowerCase().contains(lcTerm) ||
                  x.getOrElse("email", new JsString("")).as[String].toLowerCase().contains(lcTerm))
              }
            }).take(limit))).as("application/json")
          } else {
            if (response.status == 404) {

              NotFound(toJson(Map("failure" -> { "People not found" }))).as("application/json")

            } else {
              InternalServerError(toJson(Map("failure" -> { "Status: " + response.status.toString() + " returned from SEAD /api/people service" }))).as("application/json")
            }
          }
      }
      result
    } else { //TBD - just get list of Clowder users
      /*  val lcTerm = term.toLowerCase()
      Future(Ok(Json.toJson(userService.list.map(jsonPerson).filter((x) => {
        if (term.length == 0) {
          true
        } else {
          Logger.debug(lcTerm)

          (((x \ "name").as[String].toLowerCase().contains(lcTerm)) ||
            (x \ "@id").as[String].toLowerCase().contains(lcTerm) ||
            (x \ "email").as[String].toLowerCase().contains(lcTerm))
        }
      }).take(limit))).as("application/json"))
      */
      Future(NotFound(toJson(Map("failure" -> { "People not found" }))).as("application/json"))
    }
  }

  def jsonPerson(user: User): JsObject = {
    Json.obj(
      "name" -> user.fullName,
      "@id" -> user.id.stringify,
      "email" -> user.email)
  }
}

package api

import java.net.URL
import java.util.Date

import javax.inject.Inject
import models._
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}
import services._

class MetadataGroup @Inject() (
  files: FileService,
  datasets: DatasetService,
  metadataService: MetadataService,
  contextService: ContextLDService,
  events: EventService,
  mdGroups: MetadataGroupService) extends ApiController {

  def get(id: UUID) = PermissionAction(Permission.ViewMetadaGroup, Some(ResourceRef(ResourceRef.metadataGroup, id))) { implicit request =>
    val user = request.user
    user match {
      case Some(u) => {
        mdGroups.get(id) match {
          case Some(mdgroup) => {
            Ok(toJson(mdgroup.id))
          }
          case None => BadRequest("No metadatagroup with id")
        }
      }
      case None => BadRequest("No user supplied")
    }
  }

  def listGroups() = PrivateServerAction{ implicit request =>
    request.user match {
      case Some(user) => {
        val mdgroups = mdGroups.list(user.id)
        Ok(toJson(mdgroups))
      }
      case None => {
        BadRequest("No user supplied")
      }
    }
  }

  def create() = PermissionAction(Permission.CreateMetadataGroup)  (parse.json) { implicit request =>
    Logger.debug("--- API Creating new metadata group ----")
    implicit val user = request.user
    user match {
      case Some(user) => {
        val groupCreator = user.id
        val groupLabel = (request.body \ "label").asOpt[String].getOrElse("")
        val groupKeys = (request.body \ "keys").asOpt[JsValue].get
        val mdGroup = new models.MetadataGroup(creatorId = groupCreator, label = groupLabel, attachedObjectOwner = None,
          createdAt = new Date(), lastModifiedDate = new Date(), spaces = List.empty, timeAttachedToObject = None, attachedTo = None, keys = groupKeys)
        mdGroups.save(mdGroup)
        Ok(toJson("added"))
      }
      case None => {
        Logger.error("No user supplied")
        BadRequest("No user supplied")
      }
    }
  }

  def attachToFile(groupId: UUID, fileId: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file, fileId))) { implicit request =>
    val user = request.user
    user match {
      case Some(user) => {
        mdGroups.get(groupId) match {
          case Some(mdg) => {
            files.get(fileId) match {
              case Some(file) => {
                // mdGroups.attachToFile(mdg, file.id)

                // TODO what we need for metadata
                val attachedTo = ResourceRef(ResourceRef.file, file.id)
                val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + user.id
                val creator = UserAgent(user.id, "cat:user:metadatagroup", MiniUser(user.id, user.fullName, user.avatarUrl.getOrElse(""), user.email), Some(new URL(userURI)))

                // TODO CONTEXT FOR NOW ?

                val metadataContent = mdg.keys
                val context_url = "https://clowderframework.org/contexts/metadatagroup.jsonld"
                val groupDerivedFrom: JsObject = JsObject(Seq("groupDerivedFromId"->JsString("0000")))
                var context : JsArray = new JsArray()
                context = context :+ groupDerivedFrom
                context = context :+ JsString(context_url)

                var contextId = contextService.addContext(new JsString("context name"), context)


                // TODO add context

                // TODO create metadata
                //val metadata = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creator,
                //              content, version)

                val metadata = models.Metadata(UUID.generate, attachedTo, Some(contextId), Some(new URL(context_url)),new Date(), creator,
                  metadataContent, None)

                // TODO add using metadataservice
                metadataService.addMetadata(metadata)
                events.addObjectEvent(Some(user), file.id, file.filename, EventType.ADD_METADATA_FILE.toString)


                Ok(toJson("Not implemented"))
              }
              case None => {
                BadRequest("No file with id : " + fileId)
              }
            }
          }
          case None => {
            BadRequest("No MetadataGroup with id : " + groupId)
          }
        }
      }
      case None => {
        BadRequest("No user supplied")
      }
    }
  }

  def getAttachedToFile(fileId: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, fileId))) { implicit request =>
    request.user match {
      case Some(user) => {

      }
      case None => BadRequest("No user supplied")
    }

    Ok(toJson("Not implemented"))
  }

  def attachToDataset(datasetId: UUID)=  PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) {
    Ok(toJson("Not implemented"))
  }

  def getAttachedToDataset(datasetId: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) {
    Ok(toJson("Not implemented"))
  }



}

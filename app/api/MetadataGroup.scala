package api

import java.util.Date

import javax.inject.Inject
import models.{ResourceRef, UUID}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}
import services.{DatasetService, FileService, MetadataGroupService, MetadataService}

class MetadataGroup @Inject() (
  files: FileService,
  datasets: DatasetService,
  metadata: MetadataService,
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
        val groupName = (request.body \ "name").asOpt[String].getOrElse("")
        val groupContent = (request.body \ "content").asOpt[JsValue].get
        val mdGroup = new models.MetadataGroup(creatorId = groupCreator, name = groupName, attachedObjectOwner = None,
          createdAt = new Date(), lastModifiedDate = new Date(), spaces = List.empty, timeAttachedToObject = None, attachedTo = None, content = groupContent)
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
                mdGroups.attachToFile(mdg, file.id)
                val metadataContent = mdg.content
                val context_url = "https://clowderframework.org/contexts/metadatagroup.jsonld"
                val groupDerivedFrom: JsObject = JsObject(Seq("groupDerivedFromId"->JsString("0000")))
                var context : JsArray = new JsArray()
                context :+ groupDerivedFrom
                context :+ JsString(context_url)
                files.addMetadata(fileId, metadataContent)
                // TODO add this as metadata
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

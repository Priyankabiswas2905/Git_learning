package api

import java.util.Date

import javax.inject.Inject
import models.{ResourceRef, UUID}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import services.{DatasetService, FileService, MetadataGroupService, MetadataService}

class MetadataGroup @Inject() (
  files: FileService,
  datasets: DatasetService,
  metadata: MetadataService,
  mdGroups: MetadataGroupService) extends ApiController {

  def get(id: UUID) = AuthenticatedAction {

    Ok(toJson("Not Implemented"))
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
          createdAt = new Date(), timeAttachedToObject = None, attachedTo = None, content = groupContent)
        mdGroups.save(mdGroup)
        Ok(toJson("added"))
      }
      case None => {
        Logger.error("No user supplied")
        BadRequest("No user supplied")
      }
    }
  }

  def attachToFile(groupId: UUID, fileId: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file, fileId))) {
    Ok(toJson("Not implemented"))
  }

  def getAttachedToFile(fileId: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, fileId))) {
    Ok(toJson("Not implemented"))
  }

  def attachToDataset(datasetId: UUID)=  PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) {
    Ok(toJson("Not implemented"))
  }

  def getAttachedToDataset(datasetId: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) {
    Ok(toJson("Not implemented"))
  }



}

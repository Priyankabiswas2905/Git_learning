package controllers

import javax.inject.Inject

import api.Permission
import models.{ResourceRef, UUID}
import services._


/**
 * View JSON-LD metadata for all resources.
 */
class Metadata @Inject() (
  files: FileService,
  datasets: DatasetService,
  spaces: SpaceService,
  metadata: MetadataService,
  contextLDService: ContextLDService) extends SecuredController {

  def view(id: UUID) = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    metadata.getMetadataById(id) match {
      case Some(m) => Ok(views.html.metadatald.view(List(m), true))
      case None => NotFound
    }
  }

  def file(file_id: UUID) = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    files.get(file_id) match {
      case Some(file) => {
        val mds = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.file, file_id))
        // TODO use to provide contextual definitions directly in the GUI
        val contexts = (for (md <- mds;
                             cId <- md.contextId;
                             c <- contextLDService.getContextById(cId))
          yield cId -> c).toMap

        Ok(views.html.metadatald.viewFile(file, mds))
      }
      case None => NotFound
    }
  }

  def dataset(dataset_id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, dataset_id))) { implicit request =>
    implicit val user = request.user
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        val m = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset_id))
        Ok(views.html.metadatald.viewDataset(dataset, m))
      }
      case None => NotFound
    }
  }

  def search() = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    Ok(views.html.metadatald.search())
  }

  def getMetadataBySpace(id: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(space) => {
        val metadataResults = metadata.getDefinitions(Some(id))
        Ok(views.html.manageMetadataDefinitions(metadataResults.toList, Some(id), Some(space.name)))
      }
      case None => BadRequest("The requested space does not exist. Space Id: " + id)
    }

  }

}

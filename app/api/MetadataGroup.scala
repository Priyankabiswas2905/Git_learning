package api

import javax.inject.Inject
import models.UUID
import play.api.libs.json.Json.toJson
import services.{DatasetService, FileService, MetadataService}

class MetadataGroup @Inject() (
  files: FileService,
  datasets: DatasetService,
  metadata: MetadataService) extends ApiController {

  def get(id: UUID) = AuthenticatedAction {
    Ok(toJson("Not Implemented"))
  }

  def create() = AuthenticatedAction{
    Ok(toJson("Not implemented"))
  }

  def attachToFile(fileId: UUID) = AuthenticatedAction {
    Ok(toJson("Not implemented"))
  }

  def getAttachedToFile(fileId: UUID) = AuthenticatedAction{
    Ok(toJson("Not implemented"))
  }

  def attachToDataset(datasetId: UUID)=  AuthenticatedAction {
    Ok(toJson("Not implemented"))
  }

  def getAttachedToDataset(datasetId: UUID) = AuthenticatedAction {
    Ok(toJson("Not implemented"))
  }



}

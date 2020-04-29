package services

import models.{MetadataGroup, UUID}

trait MetadataGroupService {

  def save(mdGroup: MetadataGroup) : Option[String]

  def delete(mdGroupId: UUID)

  def get(id: UUID) : Option[MetadataGroup]
}

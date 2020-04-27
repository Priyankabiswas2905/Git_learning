package services

import models.{MetadataGroup, UUID}

trait MetadataGroupService {

  def save(mdGroup: MetadataGroup)

  def delete(mdGroupId: UUID)

  def get(id: UUID) : Option[MetadataGroup]
}

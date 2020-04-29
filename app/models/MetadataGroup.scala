package models

import java.util.Date

import play.api.libs.json.{JsValue, Json, Writes}

case class MetadataGroup (
                         id: UUID = UUID.generate(),
                         creatorId: UUID,
                         name: String,
                         attachedObjectOwner: Option[UUID],
                         createdAt: Date = new Date(),
                         timeAttachedToObject: Option[Date],
                         attachedTo: Option[ResourceRef],
                         content: JsValue
                         ) {

}

object MetadataGroup {
  implicit object MetadataGroupWrites extends Writes[MetadataGroup] {
    def writes(metadataGroup: MetadataGroup) = Json.obj(
      "id" -> metadataGroup.id.toString(),
      "name" -> metadataGroup.name,
      "creatorId" -> metadataGroup.creatorId.toString,
      "createdAt" -> metadataGroup.createdAt.toString,
      "attachedTo" -> metadataGroup.attachedTo.getOrElse("").toString,
      "content" -> metadataGroup.content.toString

    )
  }
}

package models
import play.api.libs.json.{JsObject, Json, Writes}

case class Group (
  id: UUID = UUID.generate,
  groupName: String="",
  creator: UUID,
  owners: List[UUID] = List.empty[UUID],
  spaceandrole: List[UserSpaceAndRole] = List.empty[UserSpaceAndRole]

)

object Group {
  implicit object GroupWrites extends Writes[Group] {
    def writes(group: Group): JsObject = {
      Json.obj(
        "id" -> group.id,
        "groupName" -> group.groupName,
        "creator" -> group.creator)
    }
  }
}
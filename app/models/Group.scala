package models

import java.net.URL
import java.util.Date
import play.api.libs.json.{Json, JsObject, Writes, Reads, JsValue}


case class Group(
  id: UUID = UUID.generate,
  name: String = "N/A",
  description: String = "N/A",
  created: Date,
  creator: UUID, 
  userList: List[UUID] = List.empty,
  userCount: Integer,
  invitations: List[(UUID, String)] = List.empty,
  defaultRole : Role = Role.Viewer,
  spaceandrole: List[GroupSpaceAndRole] = List.empty
)

case class GroupInvite(
  id: UUID = UUID.generate,
  invite_id: String,
  email: String,
  creationTime: java.util.Date,
  expirationTime: java.util.Date
)

implicit val groupWrites = new Writes[Group]{
  def writes(group: Group) = Json.obj(
      "id" -> group.id.toString,
      "name" -> group.name,
      "description" -> group.description,
      "created" -> group.created.toString,
      "creator" -> group.creator.toString,
      "userList" -> group.userList,
      "spaceandrole" -> group.spaceandrole
    )
}
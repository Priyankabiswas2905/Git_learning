package models

import play.api.libs.json.{Json, Writes}


case class GroupSpaceAndRole(
  spaceID: UUID = null,
  roleList: List[(UUID, Role)] = List.empty
)

object GroupSpaceAndRole {
  implicit val groupSpaceAndRoleWrites = new Writes[GroupSpaceAndRole] {
    def writes(groupSpaceAndRole: GroupSpaceAndRole) = Json.obj(
      "spaceID" -> groupSpaceAndRole.spaceID.toString

    )
  }
}
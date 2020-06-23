package models

import play.api.libs.json.{Json, Writes}


case class GroupSpaceAndRole(
  id: UUID,
  spaceID: UUID = null,
  userId: UUID,
  role: Role
)

object GroupSpaceAndRole {
  implicit val groupSpaceAndRoleWrites = new Writes[GroupSpaceAndRole] {
    def writes(groupSpaceAndRole: GroupSpaceAndRole) = Json.obj(
      "spaceID" -> groupSpaceAndRole.spaceID.toString

    )
  }
}
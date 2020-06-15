package models


case class GroupSpaceAndRole(
  spaceID: UUID = null
  roleList: List[(UUID, Role)] = List.empty
)
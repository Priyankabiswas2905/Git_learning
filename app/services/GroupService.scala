package services

import models._

trait GroupService{

  def get(id: UUID): Option[Group]

  def insert(group: Group): Option[String]

  def addUserToGroup(userId: UUID, group: Group)

  def removeUserFromGroup(userId: UUID, group: Group)

  def addUserInGroupToSpaceWithRole(userId: UUID, group: Group, role: Role, spaceId: UUID)

  def count() : Long

  def list() : List[Group]

  def listByCreator(creatorId: UUID): List[Group]

  def listGroupsinSpace(spaceId: UUID) : List[Group]

  def listGroupsByUser(userId: UUID) : List[Group]


}
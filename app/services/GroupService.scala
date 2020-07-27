package services

import models._

trait GroupService{

  def get(id: UUID): Option[Group]

  def insert(group: Group): Option[String]

  def addUserToGroup(userId: UUID, group: Group)

  def removeUserFromGroup(userId: UUID, group: Group)

  def addUserInGroupToSpaceWithRole(userId: UUID, group: Group, role: Role, spaceId: UUID)

  def removeUserInGroupFromSpace(userId: UUID, group: Group, spaceId: UUID)

  def count() : Long

  def list() : List[Group]

  def listByCreator(creatorId: UUID): List[Group]

  def listGroupsinSpace(spaceId: UUID) : List[Group]

  def listGroupsByUser(userId: UUID) : List[Group]

  def addInvitationToGroup(invite : GroupInvite)

  def removeInvitationFromGroup(inviteId: UUID, groupId: UUID)

  def getInvitation(inviteId : String) : Option[GroupInvite]

  def getInvitationByGroup(group : UUID): List[GroupInvite]

  def getInvitationByEmail(email: String): List[GroupInvite]

  def processInvitation(email: String)

}
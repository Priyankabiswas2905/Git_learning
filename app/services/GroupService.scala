package services

import java.util.Date

import api.Permission.Permission
import models._

import scala.collection.mutable.ListBuffer
import scala.util.Try

trait GroupService {

  def insert(group: Group) : Option[String]

  def get(id : UUID) : Option[Group]

  def addUser(groupId: UUID, userId: UUID) : Try[Unit]

  def removeUser(groupId: UUID, userId: UUID) : Try[Unit]

  def addOwner(groupId: UUID, userId: UUID) : Try[Unit]

  def removeOwner(groupId: UUID, userId: UUID) : Try[Unit]

  def addGroupToSpace(id: UUID, role: Role, spaceId: UUID)

  def getGroupRoleInSpace(groupId: UUID, spaceId: UUID): List[Role]

  def getUserGroupRolesInSpace(userId: UUID, spaceId: UUID): List[Role]

  def removeGroupFromSpace(groupId: UUID, spaceId: UUID)

  def changeGroupRoleInSpace(groupId: UUID, role: Role, spaceId: UUID)

  def listGroupsInSpace(spaceId: UUID) : List[Group]

  def listAllGroups() : List[Group]

  def listCreator(creatorId: UUID) : List[Group]

  def listOwnerOrCreator(userId: UUID) : List[Group]

  def listMember(userId: UUID) : List[Group]




}

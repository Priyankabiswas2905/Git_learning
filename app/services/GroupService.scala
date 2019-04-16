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

  def addGroupToSpace(id: UUID, role: Role, spaceId: UUID)

  def getGroupRoleInSpace(groupId: UUID, spaceId: UUID): Option[Role]

  def removeGroupFromSpace(groupId: UUID, spaceId: UUID)

  def changeGroupRoleInSpace(groupId: UUID, role: Role, spaceId: UUID)

  def listGroupsInSpace(spaceId: UUID) : List[Group]




}

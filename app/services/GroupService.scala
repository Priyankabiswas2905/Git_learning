package services

import api.Permission.Permission
import models._
import models.Collection
import models.Dataset
import models.User
import models.Role
import scala.collection.mutable.ListBuffer

trait GroupService{

  def get(id: UUID): Option[Group]

  def insert(group: Group): Option[String]

  def count() : Long

  def list() : List[Group]

  def listByCreator(creatorId: UUID): List[Group]

  def listGroupsinSpace(spaceId: UUID) : List[Group]

  def listGroupsByUser(userId: UUID) : List[Group]


}
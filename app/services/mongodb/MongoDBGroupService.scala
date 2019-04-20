package services.mongodb


import javax.inject.{Inject, Singleton}
import models.Group
import services.GroupService
import api.Permission
import api.Permission.Permission
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import models._
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import java.util.Date

import org.bson.types.ObjectId
import play.api.Logger
import util.{Formatters, SearchUtils}

import scala.collection.mutable.ListBuffer
import scala.util.Try
import services._
import javax.inject.{Inject, Singleton}

import scala.util.Failure
import scala.util.Success
import MongoContext.context
import com.mongodb.DBObject
import play.api.Play._

@Singleton
class MongoDBGroupService @Inject() (
  userService: UserService,
  spaceService: SpaceService

  ) extends GroupService {

  def insert(group: Group): Option[String] =  {
    Group.insert(group).map(_.toString)
  }

  def get(id: UUID): Option[Group] = {
    Group.findOneById(new ObjectId(id.stringify))
  }

  def listAllGroups() : List[Group] = {
    Group.findAll().toList
  }

  def listCreator(creatorId: UUID) : List[Group] = {
    Group.findAll().toList.filter((g: Group) => (g.creator == creatorId))
  }

  def listOwnerOrCreator(userId: UUID) : List[Group] = {
    Group.findAll().toList.filter((g: Group) => ((g.creator == userId)||(g.owners.contains(userId))))
  }

  def listMember(userId: UUID) : List[Group] = {
    Group.findAll().toList.filter((g: Group) => (g.members.contains(userId) || g.owners.contains(userId) || g.creator == userId))
  }

  def addUser(groupId: UUID, userId: UUID) = Try {
    Group.findOneById(new ObjectId(groupId.stringify)) match {
      case Some(group) => {
        userService.get(userId) match {
          case Some(user) => {
            if (! group.members.contains(user.id.stringify)){
              Group.update(MongoDBObject("_id" -> new ObjectId(groupId.stringify)), $addToSet("members" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
              Success
            } else {
              Failure
            }
          }
          case None => {
            Failure
          }
        }
      }
      case None => {
        Failure
      }
    }
  }

  def removeUser(groupId: UUID, userId: UUID) = Try {
    Group.findOneById(new ObjectId(groupId.stringify)) match {
      case Some(group) => {
        userService.get(userId) match {
          case Some(user) => {
            if (group.members.contains(user.id)){
              Group.update(MongoDBObject("_id" -> new ObjectId(groupId.stringify)), $pull("members" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)

              Success
            } else {
              Failure
            }
          }
          case None => {
            Failure
          }
        }
      }
      case None => {
        Failure
      }
    }
  }

  def addOwner(groupId: UUID, userId: UUID) = Try {
    Group.findOneById(new ObjectId(groupId.stringify)) match {
      case Some(group) => {
        userService.get(userId) match {
          case Some(user) => {
            if (! group.owners.contains(user.id.stringify)){
              Group.update(MongoDBObject("_id" -> new ObjectId(groupId.stringify)), $addToSet("owners" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
              Success
            } else {
              Failure
            }
          }
          case None => {
            Failure
          }
        }
      }
      case None => {
        Failure
      }
    }
  }

  def removeOwner(groupId: UUID, userId: UUID) = Try {
    Group.findOneById(new ObjectId(groupId.stringify)) match {
      case Some(group) => {
        userService.get(userId) match {
          case Some(user) => {
            if (group.members.contains(user.id)){
              Group.update(MongoDBObject("_id" -> new ObjectId(groupId.stringify)), $pull("owners" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)

              Success
            } else {
              Failure
            }
          }
          case None => {
            Failure
          }
        }
      }
      case None => {
        Failure
      }
    }
  }


  def listGroupsInSpace(spaceId: UUID) : List[Group] = {
    val retList: ListBuffer[Group] = ListBuffer.empty
    for (aGroup <- Group.find(MongoDBObject())) {
      for (aSpaceAndRole <- aGroup.spaceandrole) {
        if (aSpaceAndRole.spaceId == spaceId) {
          retList += aGroup
        }
      }
    }
    retList.toList
  }

  def getGroupRoleInSpace(groupId: UUID, spaceId: UUID): List[Role] = {

    var retRoles: ListBuffer[Role] = ListBuffer.empty[Role]

    Group.findOneById(new ObjectId(groupId.stringify)) match {
      case Some(group) => {
        for (aSpaceAndRole <- group.spaceandrole){
          if (aSpaceAndRole.spaceId == spaceId){
            retRoles += aSpaceAndRole.role
          }
        }
      }
      case None => Logger.debug("No user found for getRoleInSpace")
    }

    retRoles.toList
  }

  def getUserGroupRolesInSpace(userId: UUID, spaceId: UUID): List[Role] = {

    var retRoles: ListBuffer[Role] = ListBuffer.empty[Role]

    userService.get(userId) match {
      case Some(user) => {
        val userGroups = Group.findAll().toList.filter((g: Group) => (g.members.contains(userId) || g.owners.contains(userId) || g.creator == userId))
        for (group <- userGroups){
          for (aSpaceRole <- group.spaceandrole){
            if (aSpaceRole.spaceId == spaceId){
              retRoles += aSpaceRole.role
            }
          }
        }
      }
      case None => Logger.info("No user found")
    }

    retRoles.toList
  }

  def changeGroupRoleInSpace(groupId: UUID, role: Role, spaceId: UUID): Unit = {
    Group.dao.update(MongoDBObject("_id" -> new ObjectId(groupId.stringify), "spaceandrole.spaceId" -> new ObjectId(spaceId.stringify)),
      $set({"spaceandrole.$.role" -> RoleDAO.toDBObject(role)}), false, true, WriteConcern.Safe)
  }

  def addGroupToSpace(groupId: UUID, role: Role, spaceId: UUID): Unit = {
    Logger.debug("add group to space")
    val spaceData = UserSpaceAndRole(spaceId, role)
    val result = Group.dao.update(MongoDBObject("_id" -> new ObjectId(groupId.stringify)), $push("spaceandrole" -> UserSpaceAndRoleData.toDBObject(spaceData)));
  }

  /**
    * @see app.services.UserService
    *
    * Implementation of the UserService trait.
    *
    */
  def removeGroupFromSpace(groupId: UUID, spaceId: UUID): Unit = {
    Logger.debug("remove group from space")
    Group.dao.update(MongoDBObject("_id" -> new ObjectId(groupId.stringify)),
      $pull("spaceandrole" ->  MongoDBObject( "spaceId" -> new ObjectId(spaceId.stringify))), false, false, WriteConcern.Safe)
  }
}

object Group extends ModelCompanion[Group, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Group, ObjectId](collection = x.collection("groups")) {}
  }
}


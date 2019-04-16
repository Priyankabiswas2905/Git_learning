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

  def getGroupRoleInSpace(groupId: UUID, spaceId: UUID): Option[Role] = {
    var retRole: Option[Role] = None
    var found = false

    Group.findOneById(new ObjectId(groupId.stringify)) match {
      case Some(group) => {
        for (aSpaceAndRole <- group.spaceandrole){
          if (!found){
            if (aSpaceAndRole.spaceId == spaceId){
              retRole = Some(aSpaceAndRole.role)
              found = true
            }
          }
        }
      }
      case None => Logger.debug("No user found for getRoleInSpace")
    }

    retRole
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


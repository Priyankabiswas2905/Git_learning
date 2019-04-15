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
              Group.update(MongoDBObject("_id" -> new ObjectId(userId.stringify)), $addToSet("members" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
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
}

object Group extends ModelCompanion[Group, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Group, ObjectId](collection = x.collection("groups")) {}
  }
}


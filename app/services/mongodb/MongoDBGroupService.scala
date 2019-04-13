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

    ) extends GroupService {

    def insert(group: Group): Option[String] =  {
      Group.insert(group).map(_.toString)
    }
}

object Group extends ModelCompanion[Group, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Group, ObjectId](collection = x.collection("groups")) {}
  }
}


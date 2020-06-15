package services.mongodb


import javax.inject.{Inject, Singleton}
import api.Permission
import api.Permission.Permission
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.DBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models._
import org.bson.types.ObjectId
import play.api.Logger
import play.api.i18n.Messages
import play.{Logger => log}
import play.api.Play._
import securesocial.controllers.Registration
import securesocial.controllers.Registration._
import securesocial.core.providers.utils.RoutesHelper
import services._
import MongoContext.context
import models.Collection
import models.Dataset
import models.Role
import models.User
import util.Formatters
import scala.collection.mutable.ListBuffer
import scala.util.control._

@Singleton
class MongoDBGroupService @Inject() (
  collections: CollectionService,
  files: FileService,
  datasets: DatasetService,
  users: UserService,
  curations: CurationService,
  metadatas: MetadataService,
  events: EventService) extends GroupService {


  def get(id:UUID) : Option[Group] = {
  	GroupDAO.findOneById(new ObjectId(id.stringify))
  }

  def insert(group : Group): Option[String] = {
    GroupDAO.insert(group).map(_.toString)
  }

  def count() : Long = {
  	GroupDAO.count(MongoDBObject())
  }

  //Probably not a great idea?
  def list() : List[Group] = {
  	var GroupList = List.empty[Group]
  	GroupList = GroupDAO.findAll().toList
  	GroupList
  }

  // What decides if the "creatorId" key-value pair is input in the Mongo DB document?
  def listByCreator(creatorId: UUID) : List[Group] = {
  	var GroupList = List.empty[Group]
  	val filter = MongoDBObject("creator" -> new ObjectId(creatorId.stringify))
  	GroupList = GroupDAO.find(filter).toList
  	GroupList
  }

  //Possibly inefficient solution. This lists all groups and checks 
  def listGroupsinSpace(spaceId: UUID) : List[Group] = {
  	var GroupList = GroupDAO.findAll().toList
  	var GroupsInSpace = new ListBuffer[Group]()
  	for (group <- GroupList) {
  		var SpaceRole = group.spaceandrole
  		val result = SpaceRole.exists(_.spaceId == spaceID)
  		if(result == true) GroupsInSpace += group
  	}
  	SpaceRoleList = SpaceRole.toList
  	SpaceRoleList
  }

 def listGroupsByUser(userId: UUID) : List[Group] = {
 	var GroupList = GroupDAO.findAll().toList
 	var GroupsWithUser = new ListBuffer[Group]()
 	for (group <- GroupList if group.userList.contains(userId)) {
 		GroupsWithUser += group
 	}
 	GroupsWithUserList = GroupsWithUser.toList
 	GroupsWithUserList
 }

  object GroupDAO extends ModelCompanion[Group, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Group, ObjectId](collection = x.collection("groups")) {}
    }
  }


}


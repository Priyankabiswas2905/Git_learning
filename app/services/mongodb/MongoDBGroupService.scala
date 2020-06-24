package services.mongodb


import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.DBObject
import com.novus.salat._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.commons.{MongoDBList, MongoDBObject}
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import javax.inject.{Inject, Singleton}
import models._
import org.bson.types.ObjectId
import play.api.Play._
import services._
import services.mongodb.MongoContext.context

import scala.collection.mutable.ListBuffer

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

  def addUserToGroup(userId: UUID, group: Group) = {
    val result = GroupDAO.update(
      MongoDBObject("_id" -> new ObjectId(group.id.stringify)),
      $addToSet("userList" -> Some(new ObjectId(userId.stringify))),
      false, false)
    GroupDAO.update(MongoDBObject("_id" -> new ObjectId(group.id.stringify)), $inc("userCount" -> 1), upsert=false, multi=false, WriteConcern.Safe)
  }

  //Do not know how to do the last part. When removing a user, need to remove each of their entries in the spaecandrole's RoleList.
  // However, not sure of the syntax on how to accomplish this, as MongoDb does not support tuples.  
  def removeUserFromGroup(userId:UUID, group:Group) = {
    val result = GroupDAO.update(
      MongoDBObject("_id" -> new ObjectId(group.id.stringify)),
      $pull("userList" -> Some(new ObjectId(userId.stringify))),
      false, false, WriteConcern.Safe)
    GroupDAO.update(MongoDBObject("_id" -> new ObjectId(group.id.stringify)), $inc("userCount" -> -1), false, false, WriteConcern.Safe)
    // TODO - remove user from space and role list
    GroupDAO.update(MongoDBObject("_id" -> new ObjectId(group.id.stringify)),
      $pull("spaceandrole" ->  MongoDBObject( "userId" -> new ObjectId(userId.stringify))), false, false, WriteConcern.Safe)  }

  def addUserInGroupToSpaceWithRole(userId: UUID, group: Group, role: Role, spaceId: UUID): Unit = {
    val spaceData = GroupSpaceAndRole(UUID.generate(),spaceId, userId, role)
    val result = GroupDAO.dao.update(MongoDBObject("_id" -> new ObjectId(group.id.stringify)), $push("spaceandrole" -> GroupSpaceAndRoleData.toDBObject(spaceData)))
  }

  def removeUserInGroupFromSpace(userId: UUID, group: Group, spaceId: UUID): Unit = {
    val result = GroupDAO.dao.update(
      MongoDBObject("_id" -> new ObjectId(group.id.stringify)), 
      $pull("spaceandrole" -> MongoDBObject("spaceID" -> spaceId, "userId" -> userId)),
      false, false, WriteConcern.Safe)


    //When should I use GroupDAO.update or GroupDAO.dao.update?

    //Other implementations added to clarify some things:

    //This implementation would not work because UUID.generate() creates a new ID? How would I find the unique ID in order to use
    //the pull request with GroupSpaceAndRoleData.toDBObject method?

    //Implementation 1:
    //val spaceData = GroupSpaceAndRole(UUID.generate(),spaceId, userId, role)
    //val result = GroupDAO.dao.update(MongoDBObject("_id" -> new ObjectId(group.id.stringify)), $pull("spaceandrole" -> GroupSpaceAndRoleData.toDBObject(spaceData)))

    //This second implementation also looks good to me. However, I am not completely certain regarding defined collections, etc. 
    //Implementation 2:
    //val result =  GroupSpaceAndRoleData.remove(GroupSpaceAndRoleData.find((MongoDBObject("spaceID" -> spaceId, "userId" -> userId, "role" -> role)))
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
  		val result = SpaceRole.exists(_.spaceID == spaceId)
  		if(result == true) GroupsInSpace += group
  	}
  	GroupsInSpace.toList
  }

 def listGroupsByUser(userId: UUID) : List[Group] = {
 	var GroupList = GroupDAO.findAll().toList
 	var GroupsWithUser = new ListBuffer[Group]()
 	for (group <- GroupList if group.userList.contains(userId)) {
 		GroupsWithUser += group
 	}
 	GroupsWithUser.toList
 }

  object GroupDAO extends ModelCompanion[Group, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Group, ObjectId](collection = x.collection("groups")) {}
    }
  }

  object GroupSpaceAndRoleData extends ModelCompanion[GroupSpaceAndRole, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[GroupSpaceAndRole, ObjectId](collection = x.collection("spaceandrole")) {}
    }
  }


}


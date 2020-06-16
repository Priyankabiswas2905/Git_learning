package services.mongodb


import com.mongodb.casbah.commons.MongoDBObject
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


}


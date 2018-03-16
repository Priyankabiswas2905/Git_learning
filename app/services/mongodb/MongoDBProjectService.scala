package services.mongodb

import models.Project
import services.{DI, ProjectService}
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

/**
 * Use MongoDB to store Projects.
 */
class MongoDBProjectService extends ProjectService {

  override def getAllProjects(): List[String] = {
    Project.dao.find(MongoDBObject()).sort(orderBy = MongoDBObject("name" -> 1)).map(_.name).toList
  }

  override def addNewProject(project: String) = {
    Project.insert(new Project(project));
  }

}


object Project extends ModelCompanion[Project, ObjectId] {
  val mongoService = DI.injector.instanceOf[MongoService]
  val dao = new SalatDAO[Project, ObjectId](collection = mongoService.collection("projects")) {}
}

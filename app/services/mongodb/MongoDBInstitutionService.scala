package services.mongodb

import models.Institution
import services.{DI, InstitutionService}
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

/**
 * Use mongodb to store institutions.
 */
class MongoDBInstitutionService extends InstitutionService {

  override def getAllInstitutions(): List[String] = {
    var allinstitutions = Institution.dao.find(MongoDBObject()).sort(orderBy = MongoDBObject("name" -> 1)).map(_.name).toList
    allinstitutions match {
      case x :: xs => allinstitutions
      case nil => List("")
    }
  }

  override def addNewInstitution(institution: String) = {
    Institution.insert(new Institution(institution));
  }

}


object Institution extends ModelCompanion[Institution, ObjectId] {
  val mongoService = DI.injector.instanceOf[MongoService]
  val dao = new SalatDAO[Institution, ObjectId](collection = mongoService.collection("institutions")) {}
}

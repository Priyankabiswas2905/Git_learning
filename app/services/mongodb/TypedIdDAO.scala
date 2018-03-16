package services.mongodb

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import models.TypedID
import services.DI

/**
 * Used to store Typed ID in MongoDB.
 *
 */
object TypedIDDAO extends ModelCompanion[TypedID, ObjectId] {
  val mongoService = DI.injector.instanceOf[MongoService]
  val dao = new SalatDAO[TypedID, ObjectId](collection = mongoService.collection("TypedID")) {}
}

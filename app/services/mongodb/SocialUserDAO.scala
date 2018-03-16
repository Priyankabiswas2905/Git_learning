package services.mongodb

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import models.{ClowderUser, Identity}
import play.api.Play.current
import services.DI

/**
 * Used to store securesocial users in MongoDB.
 *
 */
object SocialUserDAO extends ModelCompanion[ClowderUser, ObjectId] {
  val mongoService = DI.injector.instanceOf[MongoService]
  val dao = new SalatDAO[ClowderUser, ObjectId](collection = mongoService.collection("social.users")) {}
}

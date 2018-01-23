package services.mongodb

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import models.{ClowderUser, Identity}
import play.api.Play.current

/**
 * Used to store securesocial users in MongoDB.
 *
 */
object SocialUserDAO extends ModelCompanion[ClowderUser, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ClowderUser, ObjectId](collection = x.collection("social.users")) {}
  }
}

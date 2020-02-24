/**
 *
 */
package services.mongodb

import play.api.{Application, Logger}
import java.util.Date

import javax.inject.Inject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import play.api.Play._

import scala.Some
import org.joda.time.DateTime
import MongoContext.context
import models.{Authenticator, IdentityId}
import services.DI

/**
 * Track securesocial authenticated users in MongoDB.
 *
 *
 */
case class LocalAuthenticator(
                               authenticatorId: String,
                               identityId: IdentityId,
                               creationDate: Date,
                               lastUsed: Date,
                               expirationDate: Date)

object AuthenticatorDAO extends ModelCompanion[LocalAuthenticator, ObjectId] {
  val mongoService = DI.injector.instanceOf[MongoService]
  val dao = new SalatDAO[LocalAuthenticator, ObjectId](collection = mongoService.collection("social.authenticator")) {}

  def saveLocal(authenticator: LocalAuthenticator) {
    val localAuth = LocalAuthenticator(authenticator.authenticatorId, authenticator.identityId,
      authenticator.creationDate, authenticator.lastUsed,
      authenticator.expirationDate)
    Logger.debug("Saving authenticator")
    dao.update(MongoDBObject("authenticatorId" -> authenticator.authenticatorId), localAuth, true, false, WriteConcern.Normal)
  }

  def find(id: String): Option[LocalAuthenticator] = {
    Logger.trace("Searching Authenticator " + id)
    dao.findOne(MongoDBObject("authenticatorId" -> id)) match {
      case Some(localAuth) => {
        Some(LocalAuthenticator(localAuth.authenticatorId, localAuth.identityId,
          new DateTime(localAuth.creationDate).toDate, new DateTime(localAuth.lastUsed).toDate,
          new DateTime(localAuth.expirationDate).toDate))
      }
      case None => None
    }
  }

  def delete(id: String) {
    Logger.trace("Deleting id from Authenticator" + id)
    AuthenticatorDAO.remove(MongoDBObject("authenticatorId" -> id), WriteConcern.Normal)
  }

}

trait MongoDBAuthenticatorStoreService

class MongoDBAuthenticatorStore @Inject() (app: Application) extends MongoDBAuthenticatorStoreService {
  
  def save(authenticator: LocalAuthenticator): Either[Error, Unit] = {
    Logger.trace("Saving Authenticator " + authenticator)
    AuthenticatorDAO.saveLocal(authenticator)
    Right(())
  }
  
  def find(id: String): Either[Error, Option[LocalAuthenticator]] = {
    Logger.trace("Searching Authenticator " + id)
    Right(AuthenticatorDAO.find(id))
  }
  
  def delete(id: String): Either[Error, Unit] = {
    Logger.trace("Deleting id from Authenticator" + id)
    AuthenticatorDAO.delete(id)
    Right(())
  }

}
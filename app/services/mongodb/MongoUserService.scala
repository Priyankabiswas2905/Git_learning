package services.mongodb

import securesocial.core.{BasicProfile}
import play.api.{Application, Logger}
import securesocial.core.providers.{MailToken}
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import java.util.Date

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.commons.TypeImports.ObjectId
import play.api.Play._

import scala.Some
import MongoContext.context
import models.{ClowderUser, IdentityId, UUID}
import securesocial.core.services.{SaveMode, UserService}

import scala.concurrent.Future

/**
 * SecureSocial implementation using MongoDB.
 *
 */
case class MongoToken(
  id: Object,
  uuid: String,
  email: String,
  creationTime: Date,
  expirationTime: Date,
  isSignUp: Boolean) {
  def isExpired = expirationTime.before(new Date)
}

object TokenDAO extends ModelCompanion[MongoToken, ObjectId] {

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[MongoToken, ObjectId](collection = x.collection("social.token")) {}
  }

  def findByUUID(uuid: String): Option[MongoToken] = {
    dao.findOne(MongoDBObject("uuid" -> uuid))
  }
  
  def removeByUUID(uuid: String) {
    dao.remove(MongoDBObject("uuid" -> uuid), WriteConcern.Normal)
  }
}

class MongoUserService(application: Application) extends UserService[ClowderUser] {
  /**
   * Finds a SocialUser that maches the specified id
   *
   * @param providerId the provider id
   * @param userId the user id
   * @return an optional user
   */
  def find(providerId: String, userId: String): Option[ClowderUser] = {
    Logger.trace("Searching for user " + userId)
    SocialUserDAO.findOne(MongoDBObject("identityId.userId"->userId, "identityId.providerId"->providerId))
  }

  /**
   * Finds a Social user by email and provider id.
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation.
   *
   * @param email - the user email
   * @param providerId - the provider id
   * @return
   */  
  def findByEmailAndProvider(email: String, providerId: String): Option[ClowderUser] = {
    Logger.trace("Searching for user " + email + " " + providerId)
    SocialUserDAO.findOne(MongoDBObject("email"->email, "identityId.providerId"->providerId))
  }

  /**
   * Saves the user.  This method gets called when a user logs in.
   * This is your chance to save the user information in your backing store.
   * @param profile
   */
  def save(profile: ClowderUser, mode: SaveMode): ClowderUser = {
    Logger.trace("Saving user " + profile.fullName)
    val query = MongoDBObject("identityId.userId"->profile.identityId.userId, "identityId.providerId"->profile.identityId.providerId)
    val dbobj = MongoDBObject("$set" -> SocialUserDAO.toDBObject(profile))
    SocialUserDAO.update(query, dbobj, upsert=true, multi=false, WriteConcern.Safe)
    profile
  }

  /**
   * Saves a token.  This is needed for users that
   * are creating an account in the system instead of using one in a 3rd party system.
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   * @param token The token to save
   * @return A string with a uuid that will be embedded in the welcome email.
   */
  def saveToken(token: MailToken): Future[MailToken] = {
    Logger.trace("Saving token " + token)
    TokenDAO.save(MongoToken(new ObjectId, token.uuid, token.email, token.creationTime.toDate, token.expirationTime.toDate, token.isSignUp))
  }


  /**
   * Finds a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   * @param token the token id
   * @return
   */
  def findToken(token: String): Option[MailToken] = {
    Logger.trace("Searching for token " + token)
    TokenDAO.findByUUID(token) match {
      case Some(t) => Some(MailToken(t.id.toString, t.email, new DateTime(t.creationTime), new DateTime(t.expirationTime), t.isSignUp))
      case None => None
    }
  }

  /**
   * Deletes a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   * @param uuid the token id
   */
  def deleteToken(uuid: String) {
    Logger.debug("----Deleting token " + uuid)
    TokenDAO.removeByUUID(uuid)
  }

  /**
   * Deletes all expired tokens
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   */
  def deleteExpiredTokens() {
    Logger.debug("----Deleting expired tokens")
    for (token <- TokenDAO.findAll) if (token.isExpired) TokenDAO.remove(token)
  }  
}

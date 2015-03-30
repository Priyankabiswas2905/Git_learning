package services.mongodb

import java.util.Date

import com.mongodb.DBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models.{MediciUser, UUID, User}
import org.bson.types.ObjectId
import play.api.Application
import play.api.Play.current
import securesocial.core.providers.Token
import securesocial.core._
import services.{DI, UserService}
import services.mongodb.MongoContext.context
import util.Direction._

/**
 * Wrapper around SecureSocial to get access to the users. There is
 * no save option since all saves should be done through securesocial
 * right now. Eventually this should become a wrapper for
 * securesocial and we use User everywhere.
 *
 * @author Rob Kooper
 */
class MongoDBUserService extends services.UserService {
  // ----------------------------------------------------------------------
  // Code to implement the common CRUD services
  // ----------------------------------------------------------------------

  override def update(model: User): Unit = {
    val query = MongoDBObject("identityId.userId" -> model.identityId.userId, "identityId.providerId" -> model.identityId.providerId)
    val dbobj = MongoDBObject("$set" -> UserDAO.toDBObject(model))
    UserDAO.update(query, dbobj, upsert = true, multi = false, WriteConcern.Safe)
  }

  override def insert(model: User): Option[String] = {
    val query = MongoDBObject("identityId.userId" -> model.identityId.userId, "identityId.providerId" -> model.identityId.providerId)
    val dbobj = MongoDBObject("$set" -> UserDAO.toDBObject(model))
    UserDAO.update(query, dbobj, upsert = true, multi = false, WriteConcern.Safe)
    UserDAO.findOne(query).map(_.id.stringify)
  }

  override def get(id: UUID): Option[User] = {
    UserDAO.findOneById(new ObjectId(id.stringify))
  }

  override def getByKey(key: String): Option[User] = {
    UserDAO.findOne(MongoDBObject("secretKey" -> key))
  }

  override def delete(id: UUID): Unit = {
    UserDAO.remove(MongoDBObject("id" -> id))
  }

  /**
   * Reset the token for the given user
   */
  def resetToken(id: UUID): String = {
    val key = _root_.java.util.UUID.randomUUID().toString
    val result = UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("secretKey" -> key))
    key
  }

  /**
   * The number of objects that are available based on the filter
   */
  override def count(filter: Option[String]): Long = {
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    UserDAO.count(filterBy)
  }

  /**
   * Return a list objects that are available based on the filter as well as the other options.
   *
   * @param order the key to use to order the data, default is natural ordering of underlying implementation
   * @param direction the direction to order the data in
   * @param start the first element that should be returned based on the order key
   * @param limit the maximum number of elements to return
   * @param filter is a json representation of the filter to be applied
   */
  override def list(order: Option[String], direction: Direction, start: Option[String], limit: Integer,
                    filter: Option[String]): List[User] = {
    val startAt = (order, start) match {
      case (Some(o), Some(s)) => {
        direction match {
          case ASC => (o $gte s)
          case DESC => (o $lte s)
        }
      }
      case (_, _) => MongoDBObject()
    }
    // what happens if we sort by user, and a user has uploaded 100 items?
    // how do we know that we need to show page 3 of that user?
    // TODO always sort by date ascending, start is based on user/start combo
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    val raw = UserDAO.find(startAt ++ filterBy)
    val orderedBy = order match {
      case Some(o) => {
        direction match {
          case ASC => raw.sort(MongoDBObject(o -> 1))
          case DESC => raw.sort(MongoDBObject(o -> -1))
        }
      }
      case None => raw
    }
    orderedBy.limit(limit).toList
  }

  // ----------------------------------------------------------------------
  // Code implementing specific functions
  // ----------------------------------------------------------------------
  /**
   * Return a specific user based on the id provided.
   */
  override def findById(id: UUID): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  /**
   * Return a specific user based on an Identity
   */
  override def findByIdentity(identity: Identity): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> identity.identityId.userId, "identityId.providerId" -> identity.identityId.providerId))
  }

  /**
   * Return a specific user based on an Identity
   */
  override def findByIdentity(userId: String, providerId: String): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> userId, "identityId.providerId" -> providerId))
  }

  /**
   * Return a specific user based on the email provided.
   */
  override def findByEmail(email: String): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("email" -> email))
  }

  override def updateUserField(email: String, field: String, fieldText: Any) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $set(field -> fieldText))
  }

  override def addUserFriend(email: String, newFriend: String) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $push("friends" -> newFriend))
  }

  override def addUserDatasetView(email: String, dataset: UUID) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $push("viewed" -> dataset))
  }

  override def createNewListInUser(email: String, field: String, fieldList: List[Any]) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $set(field -> fieldList))
  }

  object UserDAO extends ModelCompanion[User, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("social.users")) {}
    }
  }
}

class MongoDBSecureSocialUserService(application: Application) extends UserServicePlugin(application) {
  override def find(id: IdentityId): Option[Identity] = {
    UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> id.userId, "identityId.providerId" -> id.providerId))
  }

  override def findByEmailAndProvider(email: String, providerId: String): Option[Identity] = {
    UserDAO.dao.findOne(MongoDBObject("email" -> email, "identityId.providerId" -> providerId))
  }

  override def save(user: Identity): Identity = {
    // user is always of type SocialUser when this function is entered
    // first convert the socialuser object to a mongodbobject
    val userobj = com.novus.salat.grater[Identity].asDBObject(user)
    // replace _typeHint with the right model type so it will get correctly deserialized
    userobj.put("_typeHint", "models.MediciUser")
    // query to find the user based on identityId
    val query = MongoDBObject("identityId.userId" -> user.identityId.userId, "identityId.providerId" -> user.identityId.providerId)
    // update all fields from past in user object
    val dbobj = MongoDBObject("$set" -> userobj)
    // update, if it does not exist do an insert (upsert = true)
    UserDAO.update(query, dbobj, upsert = true, multi = false, WriteConcern.Safe)
    // return the user object
    find(user.identityId).get
  }

  // ----------------------------------------------------------------------
  // Code to deal with tokens
  // ----------------------------------------------------------------------
  override def deleteToken(uuid: String): Unit = {
    TokenDAO.remove(MongoDBObject("uuid" -> uuid))
  }

  override def save(token: Token): Unit = {
    TokenDAO.save(token)
  }

  override def deleteExpiredTokens(): Unit = {
    TokenDAO.remove("expirationTime" $lt new Date)
  }

  override def findToken(token: String): Option[Token] = {
    TokenDAO.findOne(MongoDBObject("uuid" -> token))
  }

  object UserDAO extends ModelCompanion[User, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("social.users")) {}
    }
  }

  object TokenDAO extends ModelCompanion[Token, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Token, ObjectId](collection = x.collection("social.token")) {}
    }
  }
}

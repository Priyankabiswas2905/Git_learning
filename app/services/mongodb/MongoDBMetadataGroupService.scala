package services.mongodb

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import javax.inject.{Inject, Singleton}
import models._
import org.bson.types.ObjectId
import play.api.Play.current
import services._
import services.mongodb.MongoContext.context

/**
 * MongoDB Metadata Service Implementation
 */
@Singleton
class MongoDBMetadataGroupService @Inject() () extends MetadataGroupService {

  def save(mdGroup: MetadataGroup): Option[String] = {
    MetadataGroupDAO.insert(mdGroup).map(_.toString)
  }

  def delete(mdGroupId: UUID): Unit = ???

  def get(id: UUID): Option[MetadataGroup] = {
    val group = MetadataGroupDAO.findOneById(new ObjectId(id.stringify))
    group
  }
}


object MetadataGroupDAO extends ModelCompanion[MetadataGroup, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[MetadataGroup, ObjectId](collection = x.collection("metadatagroup")) {}
  }
}

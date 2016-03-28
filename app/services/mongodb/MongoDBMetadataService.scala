package services.mongodb
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import play.api.Logger
import models._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import play.api.libs.json.{JsObject, JsString, Json, JsValue}
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.commons.TypeImports.ObjectId
import com.mongodb.casbah.WriteConcern
import services.MetadataService
import services.ContextLDService

/**
 * MongoDB Metadata Service Implementation
 */
@Singleton
class MongoDBMetadataService @Inject() (contextService: ContextLDService) extends MetadataService {

  /**
   * Add metadata to the metadata collection and attach to a section /file/dataset/collection
   */
  def addMetadata(metadata: Metadata): UUID = {
    // TODO: Update context
    val mid = MetadataDAO.insert(metadata, WriteConcern.Safe)
    current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin")
      case Some(x) => x.collection(metadata.attachedTo) match {
        case Some(c) => {
          c.update(MongoDBObject("_id" -> new ObjectId(metadata.attachedTo.id.stringify)), $inc("metadataCount" -> +1))
        }
        case None => {
          Logger.error(s"Could not increase counter for ${metadata.attachedTo}")
        }
      }
    }
    UUID(mid.get.toString())
  }

  def getMetadataById(id: UUID): Option[Metadata] = {
    MetadataDAO.findOneById(new ObjectId(id.stringify)) match {
      case Some(metadata) => {
        //TODO link to context based on context id
        Some(metadata)
      }
      case None => None
    }
  }

  /** Get Metadata based on Id of an element (section/file/dataset/collection) */
  def getMetadataByAttachTo(resourceRef: ResourceRef): List[Metadata] = {
    val order = MongoDBObject("createdAt"-> -1)
    MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify))).sort(order).toList
  }

  /** Get metadata based on type i.e. user generated metadata or technical metadata  */
  def getMetadataByCreator(resourceRef: ResourceRef, typeofAgent: String): List[Metadata] = {
    val order = MongoDBObject("createdAt"-> -1)
    val metadata = MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify))).sort(order)

    for (md <- metadata.toList; if (md.creator.typeOfAgent == typeofAgent)) yield md
  }

  /** Remove metadata, if this metadata does exit, nothing is executed */
  def removeMetadata(id: UUID) = {
    getMetadataById(id) match {
      case Some(md) =>    MetadataDAO.remove(md, WriteConcern.Safe)
        current.plugin[MongoSalatPlugin] match {
          case None => throw new RuntimeException("No MongoSalatPlugin")
          case Some(x) => x.collection(md.attachedTo) match {
            case Some(c) => {
              c.update(MongoDBObject("_id" -> new ObjectId(md.attachedTo.id.stringify)), $inc("metadataCount" -> -1))
            }
            case None => {
              Logger.error(s"Could not decrease counter for ${md.attachedTo}")
            }
          }
        }
    }
  }

  def removeMetadataByAttachTo(resourceRef: ResourceRef) = {
    MetadataDAO.remove(MongoDBObject("attachTo" -> resourceRef), WriteConcern.Safe)
    //not providing metaData count modification here since we assume this is to delete the metadata's host
  }


  /** Get metadata context if available  **/
  def getMetadataContext(metadataId: UUID): Option[JsValue] = {
    val md = getMetadataById(metadataId)
    md match {
      case Some(m) => {
        val contextId = m.contextId
        contextId match {
          case Some(id) => contextService.getContextById(id)
          case None => None
        }
      }
      case None => None
    }
  }

  /** Vocabulary definitions for user fields **/
  def getDefinitions(spaceId: Option[UUID] = None): List[MetadataDefinition] = {
    MetadataDefinitionDAO.findAll().toList.sortWith( _.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse("") )
  }

  def getDefinition(id: UUID): Option[MetadataDefinition] = {
    MetadataDefinitionDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  /** Add vocabulary definitions, leaving it unchanged if the update argument is set to false **/
  def addDefinition(definition: MetadataDefinition, update: Boolean = true): Unit = {
    val uri = (definition.json \ "uri").as[String]
    MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri)) match {
      case Some(md) => {
        if (update) {
          Logger.debug("Updating existing vocabulary definition: " + definition)
          // make sure to use the same id as the old value
          val writeResult = MetadataDefinitionDAO.update(MongoDBObject("json.uri" -> uri), definition.copy(id=md.id),
            false, false, WriteConcern.Normal)
        } else {
          Logger.debug("Leaving existing vocabulary definition unchanged: " + definition)
        }
      }
      case None => {
        Logger.debug("Adding new vocabulary definition " + definition)
        MetadataDefinitionDAO.save(definition)
      }
    }
  }

  /**
    * Update metadata
    * TODO: implement
    *
    * @param metadataId
    * @param json
    */
  def updateMetadata(metadataId: UUID, json: JsValue) = {}

  def deleteDefinition(id :UUID): Unit = {
    MetadataDefinitionDAO.remove(MongoDBObject("_id" ->new ObjectId(id.stringify)))
  }

  /**
    * Search by metadata. Uses mongodb query structure.
    *
    * @param query
    * @return
    */


  def search(query: JsValue): List[ResourceRef] = {
    Logger.debug("get into search using JsValue Query")
    val doc = JSON.parse(Json.stringify(query)).asInstanceOf[DBObject]
    val resources: List[ResourceRef] = MetadataDAO.find(doc).map(_.attachedTo).toList
    resources
  }

  def search(tokenList: String, count: Int): List[ResourceRef] = {
    Logger.info("get into search using token and count")

    var c = 0
    var and_or = ""
    var field = ""
    var oprtr = ""
    var regexp = ""
    var tokens = tokenList.split("\t")
    val token_length = tokenList.length()
    var idx = 0
    var doc = MongoDBObject(field -> regexp)
    var allDoc = doc
    while (c < count && idx <= token_length) {
      and_or = tokens(idx)
      idx = idx + 1
      field = "content." + tokens(idx).trim
      idx = idx + 1
      oprtr = tokens(idx)
      idx = idx + 1
      regexp = tokens(idx).trim
      idx = idx + 1

      Logger.info("4 tokens at " + c + " are: " + and_or + ", " + field + ", " + oprtr + ", " + regexp + ", ")
      if (oprtr == "=") {
        doc = MongoDBObject(field -> regexp)
      }
      else if (oprtr == "!=") {
        doc = MongoDBObject(field -> MongoDBObject("$ne" -> regexp))
      }
      else if (oprtr == ">") {
        doc = MongoDBObject(field -> MongoDBObject("$gt" -> regexp))
      }
      else if (oprtr == ">=") {
        doc = MongoDBObject(field -> MongoDBObject("$gte" -> regexp))
      }
      else if (oprtr == "<") {
        doc = MongoDBObject(field -> MongoDBObject("$lt" -> regexp))
      }
      else if (oprtr == "<=") {
        doc = MongoDBObject(field -> MongoDBObject("$lte" -> regexp))
      }
      else if (oprtr == "like") {
        val like_exp = (s"""(?i)$regexp""").r
        doc = MongoDBObject(field -> like_exp)
      }
      else {
        Logger.info("Invalid operator.")
      }
      if (c == 0) {
        allDoc = doc
      }
      else if (c > 0) {
        if (and_or == "Or") {
          Logger.info("Using Or operator")
          allDoc = MongoDBObject("$or" -> (allDoc, doc));
        }
        else if (and_or == "And") {
          Logger.info("Using And operator")
          allDoc = MongoDBObject("$and" -> (allDoc, doc));
        }
      }
      c = c+1

    }
    val resources: List[ResourceRef] = MetadataDAO.find(allDoc).map(_.attachedTo).toList
    resources
  }









  def search(key: String, operator: String, value: String): List[ResourceRef] = {
    Logger.info("get into search using 3 string values")
    val field = "content." + key.trim
    val oprtr = operator.trim
    val regexp = value.trim
    var doc = MongoDBObject(field -> regexp)
    if (oprtr == "=") {
      doc = MongoDBObject(field -> regexp)
    }
    else if (oprtr == "!=") {
      doc = MongoDBObject(field -> MongoDBObject( "$ne" -> regexp ))
    }
    else if (oprtr == ">") {
      doc = MongoDBObject(field -> MongoDBObject( "$gt" -> regexp ))
    }
    else if (oprtr == ">=") {
      doc = MongoDBObject(field -> MongoDBObject( "$gte" -> regexp ))
    }
    else if (oprtr == "<") {
      doc = MongoDBObject(field -> MongoDBObject( "$lt" -> regexp ))
    }
    else if (oprtr == "<=") {
      doc = MongoDBObject(field -> MongoDBObject( "$lte" -> regexp ))
    }
    else if (oprtr == "like") {
      val like_exp = (s"""(?i)$regexp""").r
      doc = MongoDBObject(field -> like_exp)
    }
    else {
      Logger.info("Invalid operator.")
    }
    val resources: List[ResourceRef] = MetadataDAO.find(doc).map(_.attachedTo).toList
    resources
  }

  def search(key: String, value: String, count: Int): List[ResourceRef] = {
    val field = "content." + key.trim
    val trimOr = value.trim().replaceAll(" ", "|")
    // for some reason "/"+value+"/i" doesn't work because it gets translate to
    // { "content.Abstract" : { "$regex" : "/test/i"}}
    val regexp = (s"""(?i)$trimOr""").r
    val doc = MongoDBObject(field -> regexp)
    val resources: List[ResourceRef] = MetadataDAO.find(doc).limit(count).map(_.attachedTo).toList
    resources
  }

}

object MetadataDAO extends ModelCompanion[Metadata, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[Metadata, ObjectId](collection = x.collection("metadata")) {}
  }
}

object MetadataDefinitionDAO extends ModelCompanion[MetadataDefinition, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[MetadataDefinition, ObjectId](collection = x.collection("metadata.definitions")) {}
  }
}

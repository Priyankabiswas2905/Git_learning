package services.mongodb

import java.io.InputStream
import play.api.Play._
import scala.Some
import org.bson.types.ObjectId
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import services.ThumbnailService
import models.{UUID, Thumbnail}
import MongoContext.context

/**
 * Created by lmarini on 2/27/14.
 */
class MongoDBThumbnailService extends ThumbnailService {

  def get(thumbnailId: UUID): Option[Thumbnail] = {
    Thumbnail.findOneById(new ObjectId(thumbnailId.stringify))
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => x.gridFS("thumbnails")
    }
    val mongoFile = files.createFile(inputStream)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "thumbnails")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => Some(file.inputStream,
        file.getAs[String]("filename").getOrElse("unknown-name"),
        file.getAs[String]("contentType").getOrElse("unknown"),
        file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }
}

object Thumbnail extends ModelCompanion[Thumbnail, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Thumbnail, ObjectId](collection = x.collection("thumbnails.files")) {}
  }
}

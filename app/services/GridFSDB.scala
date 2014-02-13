package services

import java.io.InputStream
import play.Logger
import play.api.Play.current
import org.bson.types.ObjectId
import models.SocialUserDAO
import models.FileDAO
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.gridfs.GridFS
import models.File
import com.mongodb.casbah.WriteConcern
import securesocial.core.Identity

/**
 * Use GridFS to store blobs.
 * 
 * @author Luigi Marini
 *
 */
trait GridFSDB {

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: Identity, showPreviews: String = "DatasetLevel"): Option[File] = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploads")
    }
    
    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)
    
    val mongoFile = files.createFile(inputStream)
    Logger.debug("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.put("showPreviews", showPreviews)
    mongoFile.put("author", SocialUserDAO.toDBObject(author))
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get
    
    // FIXME Figure out why SalatDAO has a race condition with gridfs
//    Logger.debug("FILE ID " + oid)
//    val file = FileDAO.findOne(MongoDBObject("_id" -> oid))
//    file match {
//      case Some(id) => Logger.debug("FILE FOUND")
//      case None => Logger.error("NO FILE!!!!!!")
//    }
    
    Some(File(oid, None, mongoFile.filename.get, author, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length, showPreviews))
  }

  /**
   * Get blob.
   */
  def get(id: String): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "uploads")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream, 
          file.getAs[String]("filename").getOrElse("unknown-name"), 
          file.getAs[String]("contentType").getOrElse("unknown"),
          file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }
  
}

package services.mongodb

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}

import com.mongodb.gridfs.GridFS
import javax.inject.Inject
import models.UUID
import org.apache.commons.codec.binary.Hex
import org.bson.types.ObjectId
import play.Logger
import play.api.Play._
import services.{ByteStorageService, DI}

/**
 * Store the bytes in a mongo gridFS.
 *
 */
class MongoDBByteStorage @Inject() (mongoService: MongoService) extends ByteStorageService {
  /**
   * Save the bytes to mongo, the prefix is used for the collection and id is
   * ignored.
   */
  def save(inputStream: InputStream, collection: String): Option[(String, Long)] = {
    val files = new GridFS(mongoService.getDB.underlying, collection)
    val file = files.createFile(inputStream)
    file.save()
    Some((file.getId.toString, file.getLength))
  }

  /**
   * Get the bytes from Mongo
   */
  def load(id: String, collection: String): Option[InputStream] = {
    val files = new GridFS(mongoService.getDB.underlying, collection)
    val file = files.findOne(new ObjectId(id))
    Some(file.getInputStream)
  }

  /**
   * Delete actualy bytes from Mongo
   */
  def delete(id: String, collection: String): Boolean = {
    val files = new GridFS(mongoService.getDB.underlying, collection)
    files.remove(new ObjectId(id))
    true
  }
}

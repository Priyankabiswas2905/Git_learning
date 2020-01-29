package services.mongodb

import java.io.{File, FileInputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}

import com.mongodb.gridfs.GridFS
import models.UUID
import org.apache.commons.codec.binary.Hex
import org.bson.types.ObjectId
import play.Logger
import play.api.Play._
import services.ByteStorageService

/**
 * Store the bytes in a mongo gridFS.
 *
 */
class MongoDBByteStorage extends ByteStorageService {
  /**
    * Save the bytes to mongo, the prefix is used for the collection and id is
    * ignored.
    */
  def save(inputStream: InputStream, collection: String, length: Long): Option[(String, Long)] = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        None
      }
      case Some(x) => {
        val files = new GridFS(x.getDB.underlying, collection)
        val file = files.createFile(inputStream)
        file.save()
        Some((file.getId.toString, file.getLength))
      }
    }
  }

  /**
   * Get the bytes from Mongo
   */
  def load(id: String, collection: String): Option[InputStream] = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        None
      }
      case Some(x) => {
        val files = new GridFS(x.getDB.underlying, collection)
        val file = files.findOne(new ObjectId(id))
        Some(file.getInputStream)
      }
    }
  }

  /**
   * Delete actualy bytes from Mongo
   */
  def delete(id: String, collection: String): Boolean = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        false
      }
      case Some(x) => {
        val files = new GridFS(x.getDB.underlying, collection)
        files.remove(new ObjectId(id))
        true
      }
    }
  }
}

package services

import java.io.InputStream

import models.UUID
import play.api.Logger

/**
 * Interface to store bytes. This is used by other services that store the metadata
 * about the bytes.
 *
 */
trait ByteStorageService {
  /**
   * Save the inputstream, returns a (path, length) to where the bytes are stored. The
   * path can be later used to load/delete the bytes
   */
  def save(inputStream: InputStream, prefix: String): Option[(String, Long)]

  /**
   * Load the bytes from the backing storage, returns an InputStream. The path
   * given is the same path as returned by save.
   */
  def load(path: String, prefix: String): Option[InputStream]

  /**
   * Delete the bytes from the backing storage, returns true if the bytes are
   * actually removed. The path given is the same path as returned by save.
   */
  def delete(path: String, prefix: String): Boolean
}

object ByteStorageService {
  lazy val storage: ByteStorageService = DI.injector.getInstance(classOf[ByteStorageService])

  /** returns (loader_id, loader, length) */
  def save(inputStream: InputStream, prefix: String) = {
    storage.save(inputStream, prefix).map(x => (x._1, storage.getClass.getName, x._2))
  }

  /** returns the inputstream */
  def load(loader: String, path: String, prefix: String): Option[InputStream] = {
    val bss = Class.forName(loader).newInstance.asInstanceOf[ByteStorageService]
    bss.load(path, prefix)
  }

  /** delete the blob */
  def delete(loader: String, path: String, prefix: String): Boolean = {
    try {
      val bss = Class.forName(loader).newInstance.asInstanceOf[ByteStorageService]
      bss.delete(path, prefix)
    } catch {
      case t: Throwable => {
        Logger.error(s"Error deleting : loader=${loader} path=${path} prefix=${prefix}", t)
        throw t
      }
    }
  }
}

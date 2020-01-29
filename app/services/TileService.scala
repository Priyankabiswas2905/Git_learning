package services

import models.{UUID, Tile}
import java.io.InputStream
import play.api.libs.json.JsValue

/**
 * Service to manipulate tiles.
 */
trait TileService {

  def get(tileId: UUID): Option[Tile]

  def updateMetadata(tileId: UUID, previewId: UUID, level: String, json: JsValue)

  def findTile(previewId: UUID, filename: String, level: String): Option[Tile]

  def findByPreviewId(previewId: UUID): List[Tile]

  def save(inputStream: InputStream, filename: String, contentLength: Long, contentType: Option[String]): String

  def getBlob(id: UUID): Option[(InputStream, String, String, Long)]

  def remove(id: UUID)
}

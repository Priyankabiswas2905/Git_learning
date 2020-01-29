package services

import models.{UUID, ThreeDGeometry, ThreeDTexture}
import java.io.InputStream
import play.api.libs.json.JsValue

/**
 * Service to manipulate 3-D's
 */
trait ThreeDService {

  def getTexture(textureId: UUID): Option[ThreeDTexture]

  def findTexture(fileId: UUID, filename: String): Option[ThreeDTexture]

  def findTexturesByFileId(fileId: UUID): List[ThreeDTexture]

  def updateTexture(fileId: UUID, textureId: UUID, fields: Seq[(String, JsValue)])

  def updateGeometry(fileId: UUID, geometryId: UUID, fields: Seq[(String, JsValue)])

  def save(inputStream: InputStream, filename: String, contentLength: Long, contentType: Option[String]): String

  def getBlob(id: UUID): Option[(InputStream, String, String, Long)]

  def findGeometry(fileId: UUID, filename: String): Option[ThreeDGeometry]

  def getGeometry(id: UUID): Option[ThreeDGeometry]

  def saveGeometry(inputStream: InputStream, filename: String, contentLength: Long, contentType: Option[String]): String

  def getGeometryBlob(id: UUID): Option[(InputStream, String, String, Long)]
}

/**
 *
 */
package controllers

import javax.inject.Inject

import play.api.Play._
import play.api.mvc.Controller
import api.Permission
import services.{GeostreamsService, MetadataService}
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.Logger

/**
 * View/Add/Remove Geostreams
 */
class Geostreams @Inject() (metadata: MetadataService, geostremsService: GeostreamsService)
  extends Controller with SecuredController {

  val pluginNotEnabled = InternalServerError("Geostreaming plugin not enabled")
  val enabled = current.configuration.getBoolean("geostream.enabled").getOrElse(false)

  def list() = PermissionAction(Permission.ViewSensor) { implicit request =>
    implicit val user = request.user
    if (!enabled) pluginNotEnabled
    val json: JsValue = Json.parse(geostremsService.searchSensors(None, None).getOrElse("{}"))
    val sensorResult = json.validate[List[JsValue]]
    val list = sensorResult match {
      case JsSuccess(list : List[JsValue], _) => list
      case e: JsError => {
        Logger.debug("Errors: " + JsError.toFlatJson(e).toString())
        List()
      }
    }
    Ok(views.html.geostreams.list(list))
  }

  def map() = PermissionAction(Permission.ViewSensor) { implicit request =>
    implicit val user = request.user
    if (!enabled) pluginNotEnabled
    val json: JsValue = Json.parse(geostremsService.searchSensors(None, None).getOrElse("{}"))
    val sensorResult = json.validate[List[JsValue]]
    val list = sensorResult match {
      case JsSuccess(list : List[JsValue], _) => list
      case e: JsError => {
        Logger.debug("Errors: " + JsError.toFlatJson(e).toString())
        List()
      }
    }
    Ok(views.html.geostreams.map(list))
  }

  def newSensor() = PermissionAction(Permission.CreateSensor) { implicit request =>
    implicit val user = request.user
    if (enabled) Ok(views.html.geostreams.create()) else pluginNotEnabled
  }

  def edit(id: String)= PermissionAction(Permission.CreateSensor) { implicit request =>
    implicit val user = request.user
    if (!enabled) pluginNotEnabled
    val sensor = Json.parse(geostremsService.getSensor(id).getOrElse("{}"))
    val stream_ids: JsValue = Json.parse(geostremsService.getSensorStreams(id).getOrElse("[]"))

    val streamsResult = stream_ids.validate[List[JsValue]]
    val list = streamsResult match {
      case JsSuccess(list : List[JsValue], _) => list
      case e: JsError => {
        Logger.debug("Errors: " + JsError.toFlatJson(e).toString())
        List()
      }
    }
    val definitions = metadata.getDefinitions()
    Logger.debug(list.toString)
    val streams = list.map { stream =>
      // val stream_id = (stream \ "stream_id").toString
      Json.parse(geostremsService.getStream((stream \ "stream_id").toString).getOrElse("{}"))
    }
    Ok(views.html.geostreams.edit(sensor, streams, definitions))
  }

}
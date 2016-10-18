package api

import play.api.mvc.Controller
import play.api.Play.current
import services.GeostreamsPlugin

/**
 * Metadata about sensors registered with the system. Datastreams can be associalted with sensors.
 */
object Sensors extends Controller with ApiController {

  def add() = PermissionAction(Permission.AddGeoStream)(parse.json) { implicit request =>
      current.plugin[GeostreamsPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
  }
  
  def get(id: String) = PermissionAction(Permission.ViewGeoStream)(parse.json) { implicit request =>
      current.plugin[GeostreamsPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
  }
  
  def list() = PermissionAction(Permission.ViewGeoStream)(parse.json) { implicit request =>
      current.plugin[GeostreamsPlugin] match {
        case Some(plugin) => {
          val sensors = plugin.listSensors()
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
  }
  
  def search() = PermissionAction(Permission.ViewGeoStream)(parse.json) { implicit request =>
      current.plugin[GeostreamsPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
  }
  
  def delete(id: String) = PermissionAction(Permission.DeleteGeoStream)(parse.json) { implicit request =>
      current.plugin[GeostreamsPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
  }
  
}
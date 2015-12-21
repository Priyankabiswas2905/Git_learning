package api

import play.api.mvc.Controller
import play.api.Play.current
import play.api.libs.json.Json.toJson
import services.{AppConfiguration, AppConfigurationService}
import services.mongodb.MongoSalatPlugin
import play.api.Logger

/**
 * Admin endpoints for JSON API.
 *
 * @author Luigi Marini
 */
object Admin extends Controller with ApiController {

  /**
   * DANGER: deletes all data, keep users.
   */
  def deleteAllData = ServerAdminAction { implicit request =>
    current.plugin[MongoSalatPlugin].map(_.dropAllData())
    Ok(toJson("done"))
  }
  
  
  def removeAdmin = ServerAdminAction(parse.json) { implicit request =>
    Logger.debug("Removing admin")

    request.user match {
      case Some(user) => {
        if(user.email.nonEmpty && AppConfiguration.checkAdmin(user.email.get)) {
          (request.body \ "email").asOpt[String].map { email =>
            AppConfiguration.checkAdmin(email) match {
              case true => {
                Logger.debug("Removing admin with email " + email)
                AppConfiguration.removeAdmin(email)

                Ok(toJson(Map("status" -> "success")))
              }
              case false => {
                Logger.info("Identified admin does not exist.")
                Ok(toJson(Map("status" -> "notmodified")))
              }
            }
          }.getOrElse {
            BadRequest(toJson("Missing parameter [email]"))
          }
        } else {
          Unauthorized("Not authorized")
        }
      }
      case None => Unauthorized("Not authorized")
    }
  }
  
  
  def submitAppearance = ServerAdminAction(parse.json) { implicit request =>
    (request.body \ "displayName").asOpt[String] match {
      case Some(displayName) => AppConfiguration.setDisplayName(displayName)
    }
    (request.body \ "welcomeMessage").asOpt[String] match {
      case Some(welcomeMessage) => AppConfiguration.setWelcomeMessage(welcomeMessage)
    }
    Ok(toJson(Map("status" -> "success")))
  }

  def sensorsConfig = ServerAdminAction(parse.json) { implicit request =>
    (request.body \ "sensors").asOpt[String] match {
      case Some(sensors) => AppConfiguration.setSensorsTitle(sensors)
    }
    (request.body \ "sensor").asOpt[String] match {
      case Some(sensor) => AppConfiguration.setSensorTitle(sensor)
    }
    (request.body \ "variableDefinition").asOpt[String] match {
      case Some(variableDefinition) => AppConfiguration.setVariableDefinition(variableDefinition)
      case None => AppConfiguration.removeVariableDefinition()
    }

    Ok(toJson(Map("status" -> "success")))
  }

}


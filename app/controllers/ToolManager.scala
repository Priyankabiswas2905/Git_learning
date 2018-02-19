package controllers

import javax.inject.Inject

import api.Permission
import models.{ResourceRef, UUID}
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json._
import services.{ToolInstance, ToolManagerService}

import scala.collection.immutable._
import scala.collection.mutable.{Map => MutableMap}

/**
  * A dataset is a collection of files and streams.
  */
class ToolManager @Inject() (toolService: ToolManagerService) extends SecuredController {

  /**
    * With permission, prepare Tool Manager page with list of currently running tool instances.
    */
  def toolManager() = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user

    var instanceMap = MutableMap[UUID, ToolInstance]()
    var toolList: JsObject = JsObject(Seq[(String, JsValue)]())
    // Get mapping of instanceIDs to URLs API has returned
    toolService.refreshActiveInstanceListFromServer()
    toolList = toolService.toolList
    instanceMap = toolService.instanceMap

    Ok(views.html.toolManager(toolList, instanceMap.keys.toList, instanceMap))
  }

  /**
    * Construct the sidebar listing active tools relevant to the given datasetId
    *
    * @param datasetId UUID of dataset that is currently displayed
    */
  def refreshToolSidebar(datasetId: UUID, datasetName: String) = PermissionAction(Permission.ExecuteOnDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user

    // Get mapping of instanceIDs to returned URLs
    var instanceMap = MutableMap[UUID, ToolInstance]()
    // Get mapping of instanceID -> ToolInstance if datasetID is in uploadHistory
    instanceMap = toolService.getInstancesWithDataset(datasetId)
    Ok(views.html.datasets.tools(instanceMap.keys.toList, instanceMap, datasetId, datasetName))
  }

  /**
    * Send request to ToolManagerPlugin to launch a new tool instance and upload datasetID.
    */
  def launchTool(instanceName: String, tooltype: String, datasetId: UUID, datasetName: String) = PermissionAction(Permission.ExecuteOnDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user

    val hostURL = controllers.Utils.baseUrl(request)
    val userId: Option[UUID] = user match {
      case Some(u) => Some(u.id)
      case None => None
    }
    val instanceID = toolService.launchTool(hostURL, instanceName, tooltype, datasetId, datasetName, userId)
    Ok(instanceID.toString)
  }

  /**
    * Fetch list of launchable tools from Plugin.
    */
  def getLaunchableTools() = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user
    Ok(toolService.getLaunchableTools())
  }

  /**
    * Upload a dataset to an existing tool instance. Does not check for or prevent against duplication.
    */
  def uploadDatasetToTool(instanceID: UUID, datasetID: UUID, datasetName: String) =
    PermissionAction(Permission.ExecuteOnDataset, Some(ResourceRef(ResourceRef.dataset, datasetID))) { implicit request =>
    implicit val user = request.user

    val hostURL = request.headers.get("Host").getOrElse("")
    val userId: Option[UUID] = user match {
      case Some(u) => Some(u.id)
      case None => None
    }
    toolService.uploadDatasetToInstance(hostURL, instanceID, datasetID, datasetName, userId)
    Ok("request sent")
  }

  /**
    * Get full list of running instances from Plugin.
    */
  def getInstances() = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user
    val instances = toolService.getInstances()
    Ok(toJson(instances.toMap))
  }

  /**
    * Get remote URL of running instance, if available.
    */
  def getInstanceURL(instanceID: UUID) = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user
    Ok(toolService.checkForInstanceURL(instanceID))
  }

  /**
    * Send request to server to destroy instance, and remove from Plugin.
    */
  def removeInstance(toolPath: String, instanceID: UUID) = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user
    toolService.removeInstance(toolPath, instanceID)
    Ok(instanceID.toString)
  }
}
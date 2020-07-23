package controllers

import java.net.URL
import java.util.{ Calendar, Date }
import javax.inject.Inject

import api.Permission
import api.Permission._
import models._
import play.api.{ Logger, Play }
import play.api.data.Forms._
import play.api.data.{ Form, Forms }
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.i18n.Messages
import services._
import securesocial.core.providers.{ Token, UsernamePasswordProvider }
import org.joda.time.DateTime
import play.api.i18n.Messages
import play.api.libs.ws._
import services.AppConfiguration
import util.{ Formatters, Mail, Publications }

import scala.collection.immutable.List
import scala.collection.mutable.{ ArrayBuffer, ListBuffer }
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
import org.apache.commons.lang.StringEscapeUtils.escapeJava


case class groupFormData(
  name: String,
  description: String,
  groupId: Option[UUID],
  submitButtonValue : String)


case class groupInviteData(
  addresses: List[String],
  role: String,
  message: Option[String])


/**
 * Manage files.
 */
class Groups @Inject()(
  files: FileService,
  users: UserService,
  groups: GroupService,
  appConfig: AppConfigurationService) extends SecuredController {


  val groupForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
      "group_id" -> optional(Utils.CustomMappings.uuidType),
      "submitValue" -> text
    )(
      (name, description, group_id, bvalue) => groupFormData(name = name, description = description, group_id, bvalue))(
      (d: groupFormData) => Some(d.name, d.description, d.groupId, d.submitButtonValue))
  )

  val groupInviteForm = Form(
    mapping(
      "addresses" -> play.api.data.Forms.list(nonEmptyText),
      "role" -> nonEmptyText,
      "message" -> optional(text))((addresses, role, message) => groupInviteData(addresses = addresses, role = role, message = message))((d: groupInviteData) => Some(d.addresses, d.role, d.message)))

  def list(numPage : Int, limit: Int, mode:String, owner: Option[String], nextPage : Int, prevPage :Int) = UserAction(needActive = false) { implicit request =>
    implicit val requestUser = request.user
    var nextExist = false
    var prevExist = false
    val person = owner.flatMap(o => users.get(UUID(o)))
    val ownerName = person match{
      case Some(p) => Some(p.fullName)
      case None => None
    }
    var title : Option[String] = Some(Messages("list.title", Messages("groups.title")))
    val tempList : List[models.Group] = groups.listGroupsByUser(requestUser.get.id)
    val rem = tempList.length % limit
    val div = tempList.length/limit
    val maxPage = {
      if(rem == 0)
        div
      else
        div + 1
    }
    val next = numPage + 1
    val prev = numPage - 1
    if(numPage < maxPage)
      nextExist = true
    if(numPage > 1)
      prevExist = true
    if(numPage < 1 || numPage > maxPage) {
      nextExist = false
      prevExist = false
    }
    val startIndex = limit * (numPage - 1)
    val endIndex = startIndex + (limit - 1)
    val groupList : List[models.Group] = person match {
      case(Some(p)) => {
        title = Some(Messages("owner.title", p.fullName, Messages("groups.title")))
        if((numPage < maxPage) || (numPage == maxPage && rem == 0)){
          tempList.slice(startIndex, endIndex + 1)
        }
        else if(numPage == maxPage && rem != 0){
          tempList.takeRight(rem)
        }
        else
          tempList
      }
      case None => {
        InternalServerError("No user supplied")
        List.empty
      }
    }

    //val decodedGroupList = groupList.map(Utils.decodeGroupElements)
    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
    val viewMode: Option[String] =
    if (mode == null || mode == "") {
      request.cookies.get("view-mode") match {
        case Some(cookie) => Some(cookie.value)
        case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
      }
    } else {
      Some(mode)
    }
    Ok(views.html.groups.listgroups(groupList, numPage, limit, owner, ownerName, viewMode, prevExist, nextExist, title, next, prev))
  }


  def newGroup() = UserAction(needActive = false) { implicit request =>
    implicit val requestUser = request.user
    Ok(views.html.groups.newGroup(groupForm))
  }



/*The controller for the getGroup main page */

  def getGroup(id:UUID) = AuthenticatedAction{implicit request =>
    implicit val requestUser = request.user
    Ok(views.html.groups.blank())
    }

  def submitNewGroup() = AuthenticatedAction { implicit request =>
    implicit val requestUser = request.user
    requestUser match{
      case Some(identity) => {
        val userId = request.user.get.id
        request.body.asMultipartFormData.get.dataParts.get("submitValue").headOption match {
          case Some(x) => {
            x(0) match {
              case ("Create") => {
                groupForm.bindFromRequest.fold(
                  formWithErrors => BadRequest(views.html.groups.newGroup(formWithErrors)),
                  formData => {
                    val newGroup = Group(name = formData.name, description = formData.description,
                      created = new Date, creator = userId, userCount = 0)
                    groups.insert(newGroup)
                    groups.addUserToGroup(userId, newGroup)
                    Redirect(routes.Groups.getGroup(newGroup.id))
                  })
              }
              case _ => { BadRequest("Do not recognize the submit button value.") }
            }
          }
          case None => { BadRequest("Did not get any submit button value.") }
        }
      }
      case None => Redirect(routes.Groups.list()).flashing("error" -> "You are not authorized to create groups.")
    }
  }


  def listUser() = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])
        val groupList = groups.listByCreator(u.id)
        Ok(views.html.groups.blank())

      }
      case None => {
        InternalServerError("No user supplied")
      }
    }
  }

}

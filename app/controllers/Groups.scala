package controllers

import javax.inject.Inject
import models._
import services._

import scala.collection.immutable.List


case class groupFormData(
  name: String,
  description: String,
  groupId: Option[String],
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
/*
  val groupForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
      "group_id" -> optional(Utils.CustomMappings.uuidType),
      "submitValue" -> text
    )(
      (name, description, groupId, submitValue) => groupFormData(name = name, description = description, group_id, bvalue))(
      (d: groupFormData) => Some(d.name, d.description, d.groupId, d.submitButtonValue))
  )

  val groupInviteForm = Form(
    mapping(
      "addresses" -> play.api.data.Forms.list(nonEmptyText),
      "role" -> nonEmptyText,
      "message" -> optional(text))((addresses, role, message) => groupInviteData(addresses = addresses, role = role, message = message))((d: groupInviteData) => Some(d.addresses, d.role, d.message)))
*/



//  def list2(numPage : Int, limit: Int, mode:String, owner: Option[String]) = UserAction(needActive = false) { implicit request =>
//    implicit val user = request.user
//    request.user match {
//      case Some(user) => {
//
//      }
//      case None=> {
//
//      }
//    }
//    val nextExist = false
//    val prevExist = false
//    val person = owner.flatMap(o => users.get(UUID(o)))
//    val ownerName = person match{
//      case Some(p) => Some(p.fullName)
//      case None => None
//    }
//    var title : Option[String] = Some(Messages("list.title", Messages("groups.title")))
//    val tempList = groups.listGroupsByUser(UUID(user))
//    val rem = tempList.length % limit
//    val div = tempList/limit
//    val maxPage = {
//      if(rem == 0)
//        maxPage = div
//      else
//        maxPage = div + 1
//    }
//    if(numPage < 1){
//      nextExist = false
//      prevExist = false
//    }
//    if(numPage < maxPage)
//      nextExist = true
//    if(numPage > 1)
//      prevExist = true
//    val groupList = person match {
//      case(Some(p)) => {
//        title = Some(Messages("owner.title", p.fullName, Messages("groups.title")))
//        if((numPage < maxPage) || (numPage == maxPage && rem == 0)){
//          val startIndex = limit * (numPage - 1)
//          val endIndex = startIndex + (limit - 1)
//          tempList.slice(startIndex, endIndex + 1)
//        }
//        else if(numPage == maxPage && rem != 0){
//          tempList.takeRight(rem)
//        }
//      }
//      case None => {
//        InternalServerError("No user supplied")
//      }
//    }
//
//    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
//    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
//    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
//    val viewMode: Option[String] =
//    if (mode == null || mode == "") {
//      request.cookies.get("view-mode") match {
//        case Some(cookie) => Some(cookie.value)
//        case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
//      }
//    } else {
//      Some(mode)
//    }
//
//    Ok(views.html.groups.listgroups(groupList,limit, owner, ownerName, viewMode, prevExist, nextExist, title))
//  }

  def list() = UserAction(needActive = false) { implicit request =>
    request.user match {
      case Some(user) => {
        val groupList : List[Group] = groups.listGroupsByUser(user.id)
        Ok(views.html.blank())
        // Ok(views.html.groups.listgroups(groupList,10, Some(user.id.toString), Some(user.fullName), Some("tile"), false, false, Some("title")))
      }
      case None=> BadRequest("No user")
    }
  }


  def newGroup() = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    //Ok(views.html.groups.newGroup(groupForm))O
    Ok(views.html.blank())
  }


/*The controller for the getGroup main page */

  def getGroup(id:UUID) = AuthenticatedAction{implicit request =>
    implicit val user = request.user
    Ok(views.html.blank())
  }




  def listUser() = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])
        val groupList : List[Group] = groups.listByCreator(u.id)
        Ok(views.html.blank())
        // Ok(views.html.groups.listgroups(groupList, 10,Some(u.id.toString),Some(""),Some("tile"),false,false,Some("title") ))

      }
      case None => {
        InternalServerError("No user supplied")
      }
    }
  }
}

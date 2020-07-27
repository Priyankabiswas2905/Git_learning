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
      "message" -> optional(text))((addresses, message) => groupInviteData(addresses = addresses, message = message))((d: groupInviteData) => Some(d.addresses, d.message)))

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

  def getGroup(id:UUID, size: Int) = AuthenticatedAction{implicit request =>
    implicit val requestUser = request.user
    groups.get(id) match {
      case(Some(g)) => {
        val group : Group = g
        val creator = users.findById(g.creator)
        var creatorActual : User = null
        val usersInGroup = g.userList
        val spacesWithGroup = new ListBuffer[ProjectSpace]()
        for(groupRoleObject <- g.spaceandrole){
          spaces.get(groupRoleObject.spaceID) match {
            case(Some(space)) => {
              if(spacesWithGroup.contains(space) == false)
                spacesWithGroup += space
            }
            case None => {}
          }
        }
        val spaceList =
          if(spacesWithGroup.length < size) spacesWithGroup.toList
          else spacesWithGroup.toList.takeRight(size).reverse
        creator match{
          case Some(theCreator) => {
            creatorActual = theCreator
          }
          case None => Logger.error("No creator for group found.")
        }
        /*This is just a test list to try out the method. Remove after spaces getting groups is added */
        val spaceList_two = spaces.list()
        Ok(views.html.groups.mainGroup(group, usersInGroup, spaceList_two))
      }
      case None => BadRequest(views.html.notFound(Messages("group.title")))
    }
    //Ok(views.html.groups.blank())
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

  def manageGroupUsers(id :UUID) = AuthenticatedAction { implicit request =>
    implicit val requestUser = request.user
    groups.get(id) match {
      case Some(g) => {
        val creator = users.findById(g.creator)
        var creatorActual: User = null
        val usersInGroup = new ListBuffer[User]()
        val invitations = groups.getInvitationByGroup(g.id)
        creator match {
          case Some(theCreator) => {
            creatorActual = theCreator
          }
          case None => Logger.error("No creator found for group.")
        }
        for(userId <- g.userList){
          users.get(userId) match {
            case Some(user) => usersInGroup += user
            case None =>
          }
        }
        val userList = usersInGroup.toList
        Ok(views.html.groups.groupUsers(groupInviteForm, g, creator, userList, invitations))
      }
      case None => BadRequest(views.html.notFound(Messages("group.title")))
    }
    //Ok(views.html.groups.blank())
  }

  def inviteToGroup(id:UUID) = AuthenticatedAction {implicit request =>
    implicit val requestUser = request.user
    groups.get(id) match {
      case Some(g) => {
        groupInviteForm.bindFromRequest.fold(
          formWithErrors => InternalServerError(formWithErrors.toString()),
          formData => {
            formData.addresses.map{
              email =>
                securesocial.core.UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
                  case Some(member) => {
                   val user = users.findByEmail(email)
                   groups.addUserToGroup(user.get.id, g)
                    val groupTitle: String = Messages("group.title")
                   val inviteHtml = views.html.groups.groupInviteEmail(id.stringify, g.name, requestUser.get.getMiniUser, user.get.fullName)
                    Mail.sendEmail(s"[${AppConfiguration.getDisplayName}] - Added to $groupTitle", request.user, email, inviteHtml)
                  }
                  case None => {
                    val uuid = UUID.generate()
                    val TokenDurationKey = securesocial.controllers.Registration.TokenDurationKey
                    val DefaultDuration = securesocial.controllers.Registration.DefaultDuration
                    val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)
                    val token = new Token(uuid.stringify, email, DateTime.now(), DateTime.now().plusMinutes(TokenDuration), true)
                    securesocial.core.UserService.save(token)
                    val ONE_MINUTE_IN_MILLIS = 60000
                    val date: Calendar = Calendar.getInstance()
                    val t = date.getTimeInMillis()
                    val afterAddingMins: Date = new Date(t + (TokenDuration * ONE_MINUTE_IN_MILLIS))
                    val invite = GroupInvite(uuid, uuid.toString(), email, g.id, new Date(), afterAddingMins)
                    val inviteHtml = views.html.groups.groupInviteThroughEmail(uuid.stringify, g.name, requestUser.get.getMiniUser.fullName, formData.message)
                    Mail.sendEmail(Messages("mails.sendSignUpEmail.subject"), request.user, email, inviteHtml)
                    groups.addInvitationToGroup(invite)
                  }
                }
            }
            Redirect(routes.Groups.getGroup(g.id))
          })
      }
      case None => BadRequest(views.html.notFound(Messages("group.title")))
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

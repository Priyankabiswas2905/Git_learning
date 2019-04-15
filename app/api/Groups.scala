package api

import javax.inject.{Inject, Singleton}
import services._
import models._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

@Singleton
class Groups @Inject() (
                        groups: GroupService,
                        spaces: SpaceService,
                        users: UserService

                       ) extends ApiController {

  def createGroup() = PrivateServerAction (parse.json) { implicit request =>
    val user = request.user
    Logger.debug("Creating new group")

    user match {
      case Some(identity) => {
        val groupName = (request.body \ "groupName").asOpt[String]
        groupName match {
          case Some(gname) => {

            val group = Group(groupName = gname, creator=identity.id)
            groups.insert(group) match {
              case Some(id) => {
                Ok(toJson(Map("id" -> id)))
              }
              case None => BadRequest("Group was not inserted")

            }
          }
          case None => BadRequest("Groups must have a name")
        }

      }
      case None => BadRequest("No user supplied")
    }
  }

  def getGroup(id: UUID) = PrivateServerAction (parse.json) { implicit request =>
    val user = request.user
    user match {
      case Some(identity) => {
        groups.get(id) match {
          case Some(group) => {
            Ok(jsonGroup(group))
          }
          case None => BadRequest("No group found matching id : " + id)
        }
      }
      case None => BadRequest("No user supplied")
    }
  }

  def addUsersToGroup(id: UUID) = PrivateServerAction (parse.json) { implicit request =>



    Ok("unimplemented")

  }

  def removeUsersFromGroup(id : UUID) = PrivateServerAction (parse.json) { implicit request =>

    Ok("unimplemented")

  }

  def addGroupToSpace(id: UUID, spaceid: UUID) = PrivateServerAction (parse.json) { implicit request =>

    Ok("unimplemented")

  }

  def removeGroupFromSpace(id: UUID, spaceid: UUID) = PrivateServerAction (parse.json) { implicit request =>

    Ok("unimplemented")

  }

  def listGroups(creator: UUID)= PrivateServerAction (parse.json) { implicit request =>

    Ok("unimplemented")

  }

  def jsonGroup(group: Group): JsValue = {
    toJson(Map("id" -> group.id.toString, "groupName" -> group.groupName))
  }


}

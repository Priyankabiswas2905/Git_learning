package api

import javax.inject.{Inject, Singleton}
import services._
import models._
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import _root_.util.Mail

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

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

  def getGroup(id: UUID) = PrivateServerAction  { implicit request =>
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

  def getMembersOfGroup(id: UUID) = PrivateServerAction {implicit request =>
    request.user match {
      case Some(user) => {
        groups.get(id) match {
          case Some(group) => {
            val memberIds = group.members
            var members : ListBuffer[User] = ListBuffer.empty[User]
            for (memberId <- memberIds){
              users.get(id) match {
                case Some(member) => {
                  members += member
                }
                case None => Logger.debug("No user found")
              }
            }
            Ok(Json.toJson(members.map(userToJSON)))
          }
          case None => BadRequest("No group")
        }
      }
      case None => {
        BadRequest("No user supplied")
      }
    }
  }

  def addUsersToGroup(id: UUID) = PrivateServerAction (parse.json) { implicit request =>
    val user = request.user
    Logger.debug("Adding users to group")

    user match {
      case Some(user) => {

        groups.get(id) match {
          case Some(group) => {

            // TODO implement check permissions
            if (user.id == group.creator || group.owners.contains(user.id)){
              val userIdsToAdd = (request.body \ "members").asOpt[List[String]].getOrElse(List.empty[String])
              for (id <- userIdsToAdd){
                groups.addUser(group.id, UUID(id))
              }
              Ok(toJson(userIdsToAdd))
            }
            else {
              BadRequest("No permission to add users to this group")
            }
          }
          case None => {
            BadRequest("No group found")
          }
        }
      }
      case None => {
        BadRequest("No user found")
      }
    }


  }

  def removeUsersFromGroup(id : UUID) = PrivateServerAction (parse.json) { implicit request =>
    request.user match {
      case Some(user) => {

        groups.get(id) match {
          case Some(group) => {

            // TODO implement check permissions for group
            if (user.id == group.creator || group.owners.contains(user.id)) {
              val userIdsToRemove = (request.body \ "members").asOpt[List[String]].getOrElse(List.empty[String])
              for (id <- userIdsToRemove){
                groups.removeUser(group.id, UUID(id))
              }
              Ok(toJson(userIdsToRemove))
            } else {
              BadRequest("No permission to remove users from this group")
            }

          }
          case None => BadRequest("No group with that id")
        }
      }
      case None => BadRequest("No user supplied")
    }

  }

  def leaveGroup(id: UUID) = PrivateServerAction{ implicit request =>
    request.user match {
      case Some(user) => {
        groups.get(id) match {
          case Some(group) => {
            if (group.members.contains(user.id)){
              groups.removeUser(id, user.id)
              Ok(toJson("removed self from group"))
            } else {
              BadRequest("Not in that group")
            }
          }
          case None => BadRequest("No group found")
        }
      }
      case None => BadRequest("No user supplied")
    }
    Ok(toJson("Not ready"))
  }

  def addGroupToSpace(id: UUID, spaceId: UUID) = PrivateServerAction (parse.json){implicit request =>
    request.user match {
      case Some(user) => {
        groups.get(id) match {
          case Some(group) => {
            if (user.id == group.creator || group.owners.contains(user.id)){
              spaces.get(spaceId) match {
                case Some(space) => {
                  val newGroupRole = (request.body \ "role").asOpt[String]
                  newGroupRole match {
                    case Some(newrole) => {
                      val existingGroupSpaceRoles = group.spaceandrole
                      var existingRolesForSpace : ListBuffer[Role] = ListBuffer.empty[Role]
                      for (spaceandrole <- existingGroupSpaceRoles){
                        if (spaceandrole.spaceId == spaceId){
                          existingRolesForSpace += spaceandrole.role
                        }
                      }

                      // we now have the roles the group has for the space
                      users.findRoleByName(newrole) match {
                        case Some(role) => {
                          val miniRole = new MiniRole(role.id,role.name,role.description)
                          val basicRole = new Role(role.id,role.name,role.description)
                          if (existingRolesForSpace.toList.contains(basicRole)){
                            Ok(toJson("already has that role in space"))
                          } else if (existingRolesForSpace.toList.size > 0 && !existingRolesForSpace.toList.contains(role)){
                            groups.changeGroupRoleInSpace(id, basicRole, spaceId)
                            Ok(toJson("changing role"))
                          } else {
                            groups.addGroupToSpace(id, basicRole, spaceId)
                            Ok(toJson("adding role"))
                          }
                        }
                        case None => {
                          BadRequest("Role supplied does not exist")
                        }
                      }
                    }
                    case None => {
                      BadRequest("no role supplied")
                    }
                  }
                }
                case None => BadRequest("No space")
              }

            } else{
              BadRequest("No permission to add group to a spaace")
            }
          }
          case None => BadRequest("No group")
        }
      }
      case None => {
        BadRequest("No user")
      }
    }
  }

  def removeGroupFromSpace(id: UUID, spaceId: UUID) = PrivateServerAction (parse.json) { implicit request =>
    request.user match {
      case Some(user) => {
        groups.get(id) match {
          case Some(group) => {
            val currentGroupSpaceRoles = group.spaceandrole
            var wasInSpace = false
            for (eachSpaceRole <- currentGroupSpaceRoles) {
              if (eachSpaceRole.spaceId == spaceId){
                wasInSpace = true
                groups.removeGroupFromSpace(id, spaceId)
              }
            }
            Ok("removed")
          }
          case None => BadRequest("No group with that id")
        }
      }
      case None => BadRequest("No user supplied")
    }
  }

  def addOwners(id: UUID) = PrivateServerAction (parse.json) {implicit request =>
    request.user match {
      case Some(user) => {
        groups.get(id) match {
          case Some(group) => {
            if (user.id == group.creator || group.owners.contains(user.id)){
              (request.body \"owners").asOpt[List[String]] match {
                case Some(newowners) => {
                  var ownerList: List[UUID] = List.empty
                  newowners.map {
                    aOwner => if (users.get(UUID(aOwner)).isDefined) {
                      ownerList = UUID(aOwner) :: ownerList

                    }
                  }
                  for (ownerId <- ownerList){
                    groups.addOwner(group.id, ownerId)
                  }
                  Ok(toJson("added new owners"))
                }
                case None => BadRequest("No new owners to add")
              }
            } else {
              BadRequest("No permission to add owners to this group")
            }
          }
          case None => BadRequest("No group")
        }
      }
      case None => BadRequest("No user")
    }
  }

  def removeOwners(id: UUID) = PrivateServerAction (parse.json) {implicit request =>
    request.user match {
      case Some(user) => {
        groups.get(id) match {
          case Some(group) => {
            if (user.id == group.creator || group.owners.contains(user.id)){
              (request.body \"owners").asOpt[List[String]] match {
                case Some(newowners) => {
                  var ownerList: List[UUID] = List.empty
                  newowners.map {
                    aOwner => if (users.get(UUID(aOwner)).isDefined) {
                      ownerList = UUID(aOwner) :: ownerList

                    }
                  }
                  for (ownerId <- ownerList){
                    if (group.owners.contains(ownerId)){
                      groups.removeOwner(group.id, ownerId)
                    }
                  }
                  Ok(toJson("removed  owners"))
                }
                case None => BadRequest("No new owners to add")
              }
            } else {
              BadRequest("No permission to add owners to this group")
            }
          }
          case None => BadRequest("No group")
        }
      }
      case None => BadRequest("No user")
    }
  }


  def listGroupsCreator()= PrivateServerAction { implicit request =>
    request.user match {
      case Some(user) => {
        val list = for (group <- groups.listCreator(user.id))
          yield group
        Ok(toJson(list))
      }
      case None => BadRequest("No user supplied")
    }
  }

  def listAll() = PrivateServerAction { implicit request =>
    val list = for (group <- groups.listAllGroups())
      yield group
    Ok(toJson(list))
  }

  def listGroupsOwner() = PrivateServerAction {implicit request =>
    request.user match {
      case Some(user) => {
        val list = for (group <- groups.listOwnerOrCreator(user.id))
          yield group
        Ok(toJson(list))
      }
      case None => BadRequest("No user supplied")
    }
  }

  def listGroupsMember() = PrivateServerAction {implicit request =>
    request.user match {
      case Some(user) => {
        val list = for (group <- groups.listMember(user.id))
          yield group
        Ok(toJson(list))
      }
      case None => BadRequest("No user supplied")
    }
  }

  def jsonGroup(group: Group): JsValue = {
    toJson(Map("id" -> group.id.toString, "groupName" -> group.groupName,
      "members"->group.members.toList.toString, "owners"->group.owners.toList.toString))
  }

  def jsonRole(role: Role) : JsValue = {
    Json.obj("id"->role.id,"name"->role.name,"description"->role.description)
  }

  def userToJSON(user: User): JsValue = {
    var profile: Map[String, JsValue] = Map.empty
    if(user.profile.isDefined) {
      if(user.profile.get.biography.isDefined) {
        profile  = profile + ("biography" -> Json.toJson(user.profile.get.biography))
      }
      if(user.profile.get.institution.isDefined) {
        profile = profile + ( "institution" -> Json.toJson(user.profile.get.institution))
      }
      if(user.profile.get.orcidID.isDefined) {
        profile = profile + ("orcidId" -> Json.toJson(user.profile.get.orcidID))
      }
      if(user.profile.get.position.isDefined) {
        profile = profile + ("position" -> Json.toJson(user.profile.get.position))
      }
      if(user.profile.get.institution.isDefined) {
        profile = profile + ("institution" -> Json.toJson(user.profile.get.institution))
      }

    }
    Json.obj(

      "@context" -> Json.toJson(
        Map(
          "firstName" -> Json.toJson("http://schema.org/Person/givenName"),
          "lastName" -> Json.toJson("http://schema.org/Person/familyName"),
          "email" -> Json.toJson("http://schema.org/Person/email"),
          "affiliation" -> Json.toJson("http://schema.org/Person/affiliation")
        )
      ),
      "id" -> user.id.stringify,
      "firstName" -> user.firstName,
      "lastName" -> user.lastName,
      "fullName" -> user.fullName,
      "email" -> user.email,
      "avatar" -> user.getAvatarUrl(),
      "profile" -> Json.toJson(profile),
      "identityProvider" -> user.format(true)
    )


  }


}

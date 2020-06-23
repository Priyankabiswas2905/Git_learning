
// Postman to test - send a test request

// Need a constructor to create a group
// Need a function that lists all existing groups
// Need to get a group by UUID
// Add users to a group
// Remove users from a group 
// Change role of individual users -> If a User is promoted to Admin, place a (UUID, Admin) tuple in the spaceandrole field of Group model

// Testing the code 
// Use Postman to POST an API Request, and visualize whats in the database using Robo3T

// Services do all of the interactions with the database, for example:
// traits like CollectionService, but it is implemented by the MongoDBService

// Services - add something, delete something, getting all of the groups 
// API Routes - API Routes will call Services in its functions (through Dependency Injection of those Services functions) and return a
// JSON Result. This is different from controller functions which handle views and website interactions. 
// The Service functions handle the actual work with the database, API functions utilize them.

// Should define how a Group object is a JSON -> toJSON method in API class possibly

// Define an API function similar to JsonCollection , converting a Group object to a Json format

// Skip PermissionAction, etc. because Group Permissions not defined yet. Make it PrivateServerAction, which allows you to do anything
// as long as you are logged in as a user

// Use ApiController

//The DI file binds service class instances to mongo db service implementations -> bind GroupService instance to mongo db group service
// implementation 

//Alter the mongosalatplugin (a new case statement), and also add group to resourceref, and add to the DI file

// The services handle everything in the database, and the API functions/routes here just call those services to handle everything
// There is API get and service get. The service handles everything and interacts with the database, and the API class is just
// something that defines the api routes you have to link to the service. 
// Group Services : get, finduserbyid, list groups, insert, findbycreator , addusertogroup, removeuserfromgroup
// work with the user functions later


// Run the create route, the listallgroups route, and the get route. Test these - get working. 


package api 
import java.util.Date

import javax.inject.Inject
import models._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import services._

import scala.collection.immutable.List

class Groups @Inject()(spaces: SpaceService,
                       userService: UserService,
                       datasetService: DatasetService,
                       collectionService: CollectionService,
                       events: EventService,
                       datasets: DatasetService,
                       appConfig: AppConfigurationService,
                       groups: GroupService) extends ApiController {

  //What defines what is located in the request's body?
  // Also, need to add appConfig information, etc. which is not defined for groups yet
  def createGroup() = PrivateServerAction(parse.json) { implicit request =>
  Logger.debug("Creating new group")
  (request.body \ "name").asOpt[String].map{ name =>
    var g : Group = null
    //request.user is an object of User class, not ClowderUser
    implicit val user = request.user
    val description = (request.body \ "description").asOpt[String].getOrElse("")
    user match{
      case Some(identity) => {
        val creatorId = request.user.get.id
        g = Group(name = name, description = description, created = new Date(), creator = creatorId, userList = List(creatorId),
         userCount = 1, invitations = List.empty, spaceandrole = List.empty)
        //Next line : invoke the services of the GroupService instance to add the user, to actually handle the database operations
        // The above part just creates the object by grabbing stuff from the request. You have to add it to the database.
        groups.insert(g) match {
          case Some(id) => {
            Ok(toJson(Map("id" -> id)))
          }
          case None => Ok(toJson("There was an error."))
        }

      }
      case None => InternalServerError("User Not Found")
    }
  }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  // Requires instance of GroupService, declared as a lazy variable in Permissions.scala
  // Probably add more to the JSON format for Groups
  def get(id:UUID) = PrivateServerAction { implicit request =>
    groups.get(id) match {
      case Some(group) => Ok(jsonGroup(group))
      case None => BadRequest(toJson("Group Not Found"))
    }
  }

  def listEveryGroup() = PrivateServerAction { implicit request =>
    Ok(toJson(groups.list()))
  }


  //Check if user exists
  def addUser(userId: UUID, groupId: UUID) = PrivateServerAction { implicit request =>
    groups.get(groupId) match {
      case Some(group) => {
        groups.addUserToGroup(userId, group)
        Ok(jsonGroup(group))
      }
      case None => BadRequest(toJson("Group Not Found"))
    }
  }


  // make check if user is the group creator/leader/admin
  // need to make a check if User even exists
  def removeUser(userId: UUID, groupId: UUID) = PrivateServerAction {implicit request =>
    groups.get(groupId) match{
      case Some(group) => {
        // TODO add method for removing user once created
        if(group.userList.contains(userId) && group.creator != userId) {
          groups.removeUserFromGroup(userId, group)
          Ok(jsonGroup(group))
        } else {
          BadRequest("Invalid User selection")
        }
      }
      case None => BadRequest(toJson("Group Not Found"))
    }
  }

  def addGroupToSpace(groupId: UUID, spaceId: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId))) (parse.json) { implicit request =>
    request.user match {
      case Some(user) => {
        groups.get(groupId) match {
          case Some(group) => {
            val roleName = (request.body \ "role").asOpt[String].getOrElse("Viewer")
            userService.findRoleByName(roleName) match {
              case Some(role) => {
                val usersInGroup = group.userList
                for (userId <- group.userList) {
                  userService.get(userId) match {
                    case Some(user) => {
                      groups.addUserInGroupToSpaceWithRole(userId, group, role, spaceId)

                    }
                    case None =>
                  }
                }
                Ok(toJson("added group to space"))
              }
            }
          }
          case None => BadRequest("No group with that id")
        }
      }
      case None => BadRequest("No user supplied")
    }
  }

  def jsonGroup(group: Group): JsValue = {
  toJson(Map(
      "id" -> group.id.toString,
      "name" -> group.name,
      "description" -> group.description,
      "created" -> group.created.toString,
      "creator" -> group.creator.toString,
      "userCount" -> group.userCount.toString,
      "userList" -> group.userList.toString
    ))
  }
}
package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.ApiOperation
import models.{UUID, User}
import play.api.libs.json._
import play.api.mvc.Action
import services.UserService

/**
 * API to interact with the users.
 *
 * @author Rob Kooper
 */
class Users @Inject()(users: UserService) extends ApiController {
  /**
   * Returns a list of all users in the system.
   */
  @ApiOperation(value = "List all users in the system",
    responseClass = "User", httpMethod = "GET")
  def list() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.UserAdmin)) { request =>
    Ok(Json.toJson(users.list))
  }

  /**
   * Returns a single user based on the id specified.
   */
  @ApiOperation(value = "Return a single user.",
    responseClass = "User", httpMethod = "GET")
  def findById(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.findById(id) match {
      case Some(x) => Ok(Json.toJson(x))
      case None => BadRequest("no user found with that id.")
    }
  }

  /**
   * Returns a single user based on the email specified.
   */
  @ApiOperation(value = "Return a single user.",
    responseClass = "User", httpMethod = "GET")
  def findByEmail(email: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.findByEmail(email) match {
      case Some(x) => Ok(Json.toJson(x))
      case None => BadRequest("no user found with that email.")
    }
  }

  @ApiOperation(value = "Edit User Field.",
    responseClass = "None", httpMethod = "POST")
  def updateUserField(email: String, field: String, fieldText: Any) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.updateUserField(email, field, fieldText)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Add a friend.",
    responseClass = "None", httpMethod = "POST")
  def addUserFriend(email: String, newFriend: String)= SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.addUserFriend(email, newFriend)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Add a dataset View.",
    responseClass = "None", httpMethod = "POST")
  def addUserDatasetView(email: String, dataset: UUID)= SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.addUserDatasetView(email, dataset)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Create a List.",
    responseClass = "None", httpMethod = "POST")
  def createNewListInUser(email: String, field: String, fieldList: List[Any])= SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.createNewListInUser(email, field, fieldList)
    Ok(Json.obj("status" -> "success"))
  }

}

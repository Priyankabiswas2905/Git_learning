package controllers

import java.io.{PrintWriter, StringWriter}

import models.User
import play.api.http.HttpErrorHandler
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.{BadRequest, InternalServerError, NotFound}
import play.api.mvc.{RequestHeader, Result}
import services.{DI, UserService}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class OnError extends  HttpErrorHandler {

  def onClientError(request: RequestHeader, statusCode: Int, message: String = ""): Future[Result] = {

    if(statusCode == play.api.http.Status.NOT_FOUND) {
      if (request.path.contains("/api/")) {
        Future(InternalServerError(toJson(Map("status" -> "not found",
          "request" -> request.toString()))))
      } else {
        // TODO get idenitity from request
        val users: UserService = DI.injector.instanceOf[UserService]
        implicit val user = users.findByIdentity("userId", "providerId")
        Future(NotFound(views.html.errorPage(request, "Not found")(user)))
      }
    }

    if(statusCode == play.api.http.Status.BAD_REQUEST) {
      if (request.path.contains("/api/")) {
        Future(InternalServerError(toJson(Map("status" -> "bad request",
          "message" -> message,
          "request" -> request.toString()))))
      } else {
        // TODO get identity from request
        val users: UserService = DI.injector.instanceOf[UserService]
        implicit val user = users.findByIdentity("userId", "providerId")
        Future(BadRequest(views.html.errorPage(request, message)(user)))
      }
    }
  }

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    exception.printStackTrace(pw)

    if (request.path.contains("/api/")) {
      Future(InternalServerError(toJson(Map("status" -> "error",
        "request" -> request.toString(),
        "exception" -> sw.toString.replace("\n", "\\n")))))
    } else {
      // TODO get identity from request
      //      val users: UserService =  DI.injector.instanceOf[UserService]
      //      implicit val user = users.findByIdentity("userId", "providerId")
      Future(InternalServerError(views.html.errorPage(request, sw.toString)(Some(User.anonymous))))
    }
  }
}

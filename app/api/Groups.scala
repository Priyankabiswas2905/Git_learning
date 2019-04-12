package api

import javax.inject.{Inject, Singleton}
import services._
import models._

@Singleton
class Groups @Inject() (
                        spaces: SpaceService,
                        users: UserService

                       ) extends ApiController {

  def createGroup() = PrivateServerAction (parse.json) { implicit request =>

    Ok("unimplemented")

  }

  def getGroup(id: UUID) = PrivateServerAction (parse.json) { implicit request =>

    Ok("unimplemented")

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


}

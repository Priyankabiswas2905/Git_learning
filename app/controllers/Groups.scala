package controllers

import javax.inject.Inject
import services._



/**
 * Manage files.
 */
class Groups @Inject()(
  files: FileService,
  users: UserService,
  groups: GroupService,
  appConfig: AppConfigurationService) extends SecuredController {

  def list() = UserAction(needActive = false) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        val groupList = groups.list()
        Ok(views.html.groups.listgroups(groupList))

      }
      case None => {
        InternalServerError("No user supplied")
      }
    }
  }

  def listUser() = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])
        val groupList = groups.listByCreator(u.id)
        Ok(views.html.groups.listgroups(groupList))

      }
      case None => {
        InternalServerError("No user supplied")
      }
    }
  }

}

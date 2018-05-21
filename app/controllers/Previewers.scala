package controllers

import api.{Permission, UserRequest}
import javax.inject.Inject
import services.PreviewService

/**
 * Previewers.
 */
class Previewers @Inject()(previews: PreviewService) extends SecuredController {

  def list = PermissionAction(Permission.ViewFile) { implicit request: UserRequest[String] =>
    Ok(views.html.previewers(previews.findPreviewers()))
  }
}

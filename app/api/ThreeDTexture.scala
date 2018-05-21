package api

import java.io.FileInputStream

import javax.inject.Inject
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json._
import play.api.mvc.{Controller, MultipartFormData}
import services.ThreeDService

class ThreeDTexture @Inject()(threeD: ThreeDService) extends Controller with ApiController {
  
  /**
   * Upload a 3D texture file.
   */  
  def uploadTexture() =
    PermissionAction(Permission.CreatePreview) { implicit request: UserRequest[MultipartFormData[TemporaryFile]] =>
      request.body.file("File").map { f =>        
        Logger.debug("Uploading 3D texture file " + f.filename)
        // store file
        try {
          val id = threeD.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
          Ok(toJson(Map("id" -> id)))
        } finally {
          f.ref.file.delete()
        }
    }.getOrElse {
      BadRequest(toJson("File not attached."))
    }
  }
}
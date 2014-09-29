package api

import play.api.mvc.Controller
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.Logger
import javax.inject.Inject
import services.ThreeDService

class Geometry @Inject()(threeD: ThreeDService) extends Controller with ApiController {

    /**
   * Upload a 3D binary geometry file.
   */  
  def uploadGeometry() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) { request =>
      request.body.file("File").map { f =>        
        Logger.info("Uploading binary geometry file " + f.filename)
        // store file
        val id = threeD.saveGeometry(new FileInputStream(f.ref.file), f.filename, f.contentType)
        Ok(toJson(Map("id"->id)))   
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
  }
}
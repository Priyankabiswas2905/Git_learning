package api

import javax.inject.Inject
import api.Permission.Permission
import models._
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import services._
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

class Tree @Inject()(
  treeService: TreeService) extends ApiController {

  def getChildrenOfNode(nodeType: String, nodeId: Option[String], mine: Boolean) = PrivateServerAction { implicit request =>
    request.user match {
      case Some(user) => {
        var result = treeService.getChildrenOfNode(nodeType,nodeId,mine,user)
        Ok(toJson(result))
      }
      case None => BadRequest("No user supplied")
    }
  }


}

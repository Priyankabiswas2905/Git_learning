package api

import javax.inject.Inject
import play.api.libs.ws._
import com.ning.http.client.Realm.AuthScheme
import play.api.mvc.{Result, SimpleResult}
import services.EventService

import scala.concurrent.Future
// needed to return async results
import play.api.libs.concurrent.Execution.Implicits.defaultContext


/**
  * API to interact with the proxy.
  */
class Proxy @Inject()(/*proxyService: ProxyService,*/ events: EventService) extends ApiController {


  def get(endpoint: String)= AuthenticatedAction.async { implicit request =>
    request.user match {
      case Some(user) => {
        // filter out the Host and potentially any other header we don't want
        val headers: Seq[(String, String)] = request.headers.toSimpleMap.toSeq.filter {
          case (headerStr, _) if headerStr != "Host" => true
          case _ => false
        }

        val endpointCfgKey = "clowder.proxy." + endpoint
        val playConfig = play.Play.application().configuration()

        if (playConfig.keys.contains(endpointCfgKey)) {
          val proxyTarget = playConfig.getString(endpointCfgKey)

          val proxiedRequest = WS.url(proxyTarget)
            .withQueryString(request.queryString.mapValues(_.head).toSeq: _*) // similarly for query strings

          proxiedRequest.get().map { resp =>
            val contentType = resp.header("Content-Type")
            contentType match {
              case Some(cType) => {
                val response = Ok(resp.body)
                response.as(cType)
              }
              case None => {
                Ok(resp.body).as("text/html")
              }
            }
          }
        } else {
          Future(NotFound("Not found: " + endpoint))
        }
      }
      case None => {
        Future(Unauthorized("Not authenticated"))
      }
    }
  }
}

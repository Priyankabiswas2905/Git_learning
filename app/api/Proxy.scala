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
        val endpointCfgKey = "clowder.proxy." + endpoint
        val playConfig = play.Play.application().configuration()

        if (playConfig.keys.contains(endpointCfgKey)) {
          val proxyTarget = playConfig.getString(endpointCfgKey)

          val proxiedRequest = WS.url(proxyTarget)

          if (endpoint == "geoserver") {
            val gsUser = playConfig.getString("geoserver.username")
            val gsPass = playConfig.getString("geoserver.password")
            proxiedRequest.withAuth(gsUser, gsPass, AuthScheme.BASIC)
          }

          proxiedRequest.get().map { resp =>
            Ok(resp.body).as("text/html")
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

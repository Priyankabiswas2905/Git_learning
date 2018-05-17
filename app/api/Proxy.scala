package api

import com.ning.http.client.FluentCaseInsensitiveStringsMap
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee._
import play.api.libs.ws._
import javax.inject.Inject
import services.EventService

import scala.concurrent.Future

/**
  * An API that allows you to use Clowder as a reverse-proxy. The proxy can be configured by setting up the
  * "clowder.proxy" key in Clowder's configuration (e.g. your custom.conf file). Requests to "/api/proxy/:endpoint"
  * will then be routed to the specified target.
  *
  * For example:
  *   clowder.proxy {
  *     testing="https://www.google.com"
  *     rabbitmq="http://localhost:15672"
  *   }
  *
  * With the above configured, navigating to /api/proxy/testing will proxy your requests to https://www.google.com
  * and transparently send you the response to your proxied request.
  *
  */
object Proxy {
  /** The prefix to search Clowder's configuration for proxy endpoint */
  val ConfigPrefix: String = "clowder.proxy."
}

class Proxy @Inject()(events: EventService) extends ApiController {
  import Proxy.ConfigPrefix

  /** Translates an endpoint name to a target URL based on Clowder's configuration */
  def getProxyTarget(endpoint: String): String = {
    // Prefix our configuration values
    val endpointCfgKey = ConfigPrefix + endpoint
    val playConfig = play.Play.application().configuration()

    // If this endpoint has been configured, return it
    if (playConfig.keys.contains(endpointCfgKey)) {
      return playConfig.getString(endpointCfgKey)
    }

    // By default, return null
    return null
  }

  /** Proxies a GET request to the specified endpoint */
  def get(endpoint: String)= AuthenticatedAction.async { implicit request =>
    // Read Clowder configuration to retrieve the target endpoint URL
    val proxyTarget = getProxyTarget(endpoint)

    if (null == proxyTarget) {
       Future(NotFound("Not found: " + endpoint))
    } else {
      // Build our proxied GET request (preserve query string parameters)
      val proxiedRequest = WS.url(proxyTarget)
        .withQueryString(request.queryString.mapValues(_.head).toSeq: _*)

      proxiedRequest.get().map { resp =>
        // Copy all of the headers from our proxied response
        val map: FluentCaseInsensitiveStringsMap = resp.ahcResponse.getHeaders
        val contentType = map.getFirstValue("Content-Type")

        // Stream our response data (to handle large/chunked responses)
        val responseBody = resp.ahcResponse.getResponseBodyAsStream
        val chunkedResponseEnumerator = Enumerator.fromStream(responseBody)

        // TODO: other headers my be needed for specific cases
        Ok.chunked(chunkedResponseEnumerator)
          .withHeaders(
            SERVER -> map.getFirstValue("Server"),
            CONTENT_DISPOSITION -> map.getFirstValue("Content-Disposition"),
            CONTENT_TYPE -> contentType,
            CONNECTION -> map.getFirstValue("Connection"),
            TRANSFER_ENCODING -> map.getFirstValue("Transfer-Encoding"),
            "X-Frame-Options" -> map.getFirstValue("X-Frame-Options")
          )
          .as(contentType)
      }
    }
  }
}

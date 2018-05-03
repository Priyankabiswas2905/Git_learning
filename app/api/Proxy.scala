package api

import java.io._
import java.util
import java.util.Map.Entry
import java.util.function.Consumer

import com.ning.http.client.FluentCaseInsensitiveStringsMap
import com.ning.http.client.Realm.AuthScheme
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee._
import play.api.libs.ws._
import play.api.mvc.{Result, SimpleResult}
import javax.inject.Inject
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.CountingOutputStream
import services.EventService

import scala.concurrent.Future

/**
  * API to interact with the proxy. The proxy can be configured by setting the "clowder.proxy" key in
  * Clowder's configuration. Requests to "/api/proxy/:endpoint" will then be routed to the specified target.
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
class Proxy @Inject()(/*proxyService: ProxyService,*/ events: EventService) extends ApiController {

  /** Proxies a GET request to the specified endpoint */
  def get(endpoint: String)= AuthenticatedAction.async { implicit request =>
    request.user match {
      case None => {
        Future(Unauthorized("Not authenticated"))
      }
      case Some(user) => {
        val endpointCfgKey = "clowder.proxy." + endpoint
        val playConfig = play.Play.application().configuration()

        if (!playConfig.keys.contains(endpointCfgKey)) {
           Future(NotFound("Not found: " + endpoint))
        } else {
          // Read Clowder configuration to retrieve the target endpoint URL
          val proxyTarget = playConfig.getString(endpointCfgKey)

          // Build our proxied GET request (preserve query string parameters)
          val proxiedRequest = WS.url(proxyTarget)
            .withQueryString(request.queryString.mapValues(_.head).toSeq: _*)

          proxiedRequest.get().map { resp =>
            // Stream our response data (to handle large/chunked responses)
            val responseBody = resp.ahcResponse.getResponseBodyAsStream
            val chunkedResponseEnumerator = Enumerator.fromStream(responseBody)

            // Copy all of the headers from our proxied response
            val map: FluentCaseInsensitiveStringsMap = resp.ahcResponse.getHeaders
            val contentType = map.getFirstValue("Content-Type")

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
  }
}

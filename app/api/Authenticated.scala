package api

import play.api.mvc.Action
import play.api.mvc.Request
import play.api.Logger
import play.api.mvc.Results.Unauthorized
import org.apache.commons.codec.binary.Base64
import securesocial.core.SocialUser
import org.mindrot.jbcrypt.BCrypt
import securesocial.core.UserService
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.SecureSocial
import securesocial.core.SecuredRequest
import securesocial.core.AuthenticationMethod
import play.api.mvc.SimpleResult
import scala.concurrent.Future
import securesocial.core.IdentityId

/**
 * Secure API. 
 * 
 * Check in this order: basic authentication, token in url, cookie from browser session.
 * 
 * @author Luigi Marini
 *
 */
case class Authenticated[A](action: Action[A]) extends Action[A] {
  
  def apply(request: Request[A]): Future[SimpleResult] = {
    request.headers.get("Authorization") match { // basic authentication
      case Some(authHeader) => {
        val header = new String(Base64.decodeBase64(authHeader.slice(6,authHeader.length).getBytes))
        val credentials = header.split(":")
        UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword) match {
	          case Some(identity) => {
	            if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
	               action(SecuredRequest(identity, request))
	            } else {
	               Logger.debug("Password doesn't match")
	               Future.successful(Unauthorized(views.html.defaultpages.unauthorized()))
	            }
	          }
	          case None => {
	            Logger.debug("User not found")
	            Future.successful(Unauthorized(views.html.defaultpages.unauthorized()))
	          }
	        }
	      }
      case None => {
        request.queryString.get("key") match { // token in url
          case Some(key) => {
            if (key.length > 0) {
              // TODO Check for key in database
              if (key(0).equals(play.Play.application().configuration().getString("commKey"))) {
    	        action(SecuredRequest(new SocialUser(new IdentityId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword), request))
              } else {
                Logger.debug("Key doesn't match")
                Future.successful(Unauthorized(views.html.defaultpages.unauthorized()))
              }
            } else Future.successful(Unauthorized(views.html.defaultpages.unauthorized()))
          }
          case None => {
            
            SecureSocial.currentUser(request) match { // calls from browser
		      case Some(identity) => action(SecuredRequest(identity, request))
		      case None => {
		           Logger.info("Authenticated - Authentication failure")
		           Future.successful(Unauthorized(views.html.defaultpages.unauthorized()))
		      }
		    }
          }
        }
      }
    }
  }
  
  lazy val parser = action.parser
}

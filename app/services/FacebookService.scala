package services

import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import com.restfb.FacebookClient
import com.restfb.types.User
import com.restfb.types.FacebookType
import com.restfb.Parameter
import fbutils.LoggedInFacebookClient
import com.restfb.exception.FacebookGraphException
import com.restfb.DefaultFacebookClient
import models.Subscriber
import scala.collection.mutable.ArrayBuffer
import play.libs.Akka
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import controllers.routes
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import play.api.libs.json.JsObject

class FacebookService (application: Application) extends Plugin  {
  
  val subscriberService: SubscriberService = DI.injector.getInstance(classOf[SubscriberService])
  
  var appPort = play.api.Play.configuration.getString("https.port").getOrElse("")
  val httpProtocol = {
					if(!appPort.equals("")){
						"https://"
					}
					else{
						appPort = play.api.Play.configuration.getString("http.port").getOrElse("")
						"http://"
					}
		}
  
  var FBClient: Option[FacebookClient] = None
  
  override def onStart() {
    Logger.debug("Starting Facebook Plugin")
	this.FBClient = Some(new LoggedInFacebookClient())
	
	var timeInterval = play.Play.application().configuration().getInt("fb.checkAndRemoveExpired.every")
	    Akka.system().scheduler.schedule(0.days, timeInterval.intValue().days){
	      checkAndRemoveExpired()
	    }
	
  }
  
  override def onStop() {
    Logger.debug("Shutting down Facebook Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("facebookservice").filter(_ == "disabled").isDefined
  }
  
  def checkAndRemoveExpired(){
    val appName = play.Play.application().configuration().getString("fb.visibleName")
    val resubscribeAnnouncement= "Your " + appName + " subscription has expired. Go to the following link to resubscribe."
    val url= httpProtocol+play.Play.application().configuration().getString("hostIp").replaceAll("/$", "")+":"+appPort+routes.Subscribers.subscribe.url
    val name = "Subscribe"
    val thisPlugin = this 
    
    for(subscriber <- subscriberService.getAllExpiring){
      this.sendFeedToSubscriberFacebook(subscriber.FBIdentifier.get,resubscribeAnnouncement,url,"",name,"")
      subscriberService.remove(subscriber.id)
    }
  }
  
  def getIfExistsInGraph(id: String): Boolean = {
    FBClient match{
      case Some(fbClient) => {
        try{
        		fbClient.fetchObject(id, classOf[User])
        		
        		//Dummy default return
        		true
        }catch{ case ex: FacebookGraphException => {
        	val exceptionMsg = ex.toString.toLowerCase()
        	//If id found to exist (but be global), then the user exists in the graph
        	if(exceptionMsg.contains("global id") && exceptionMsg.contains("not allowed"))
        	  true
        	//If username found to exist in graph (though not queryable due to Graph 2.0 restrictions), then the user exists in the graph  
        	else if(exceptionMsg.contains("cannot query") && exceptionMsg.contains("username"))  
        	  true	
        	else
        	  false
          }}
      	}
      case None => {
        Logger.error("Could not validate user in FB graph. No active Facebook client.")
        throw new Exception("Could not validate user in FB graph. No active Facebook client.")
      }
    }
  }
  
  
  def getUsernameById(id: String): String = {
    FBClient match{
      case Some(fbClient) => {
        try{
        		fbClient.fetchObject(id, classOf[User])
        }catch{case ex: Exception =>{Logger.debug(ex.toString()); ""}}
        
        	val fbObject = fbClient.fetchObject(id, classOf[User])        	
        	//exception thrown from fetchObject if user does not exist
        	try{
        		val theUsername = fbObject.getUsername()
        		if(theUsername != null)
        		  theUsername
        		else //user exists but has no username
        		   "0"        		
        	}catch{ case ex: FacebookGraphException => {
        		//user exists but has no username
        		"0"
        	}}
      }
      case None => {
        Logger.warn("Could not get user's username by id. No active Facebook client.")
        "0"
      }
    }
  }
  def getIdByUsername(username: String): String = {    
    FBClient match{
      case Some(fbClient) => {
        try{
        fbClient.fetchObject(username, classOf[User]).getId()
        }catch{case ex: Exception =>{Logger.debug(ex.toString()); ""}}
      }
      case None => {
        Logger.warn("Could not get user's id by username. No active Facebook client.")
        "0"
      }
    }
  }
  
  def sendFeedToSubscriberFacebook(subscriberIdentifier : String, html: String, url: String, image: String, name: String, description: String): Boolean = {

		subscriberService.getAuthToken(subscriberIdentifier) match{
		  case Some(authToken) =>{
		    val visibleName = play.Play.application().configuration().getString("fb.visibleName")
		    val fbClient = new DefaultFacebookClient(authToken)
		    val fbAppId = play.Play.application().configuration().getString("fb.appId")
		    
		    var publishingParams = ArrayBuffer.empty[Parameter]
		    if(!html.equals(""))
		      publishingParams += Parameter.`with`("message", html)
		    if(!url.equals(""))
		      publishingParams += Parameter.`with`("link", url)
		    if(!image.equals(""))
		      publishingParams += Parameter.`with`("picture", image)
		    if(!name.equals(""))
		      publishingParams += Parameter.`with`("name", name)
		    if(!description.equals(""))
		      publishingParams += Parameter.`with`("description", description)
		      
		    
		    try{  //"<a href='"+visibleLink+"'><b>"+visibleName+"</b></a><br /><br />"+
		    	fbClient.publish("me"+"/feed",classOf[FacebookType], publishingParams.toArray:_*)
		    	true
		    }catch{ case ex: Exception => {
		    	Logger.error(ex.toString())
        		Logger.error("Could not send feed to subscriber. Subscriber does not exist on Facebook, or authentication token was invalid.")
        		false
        	}}		    
		  }
		  case None=>{
		    Logger.error("Subscriber or subscriber authentication token not found. Could not send feed to subscriber.")
		    false
		  }		  
		}  	
  }

  
  def getIfUserGrantedPermissions(authToken : String): Boolean = {
    
    val fbClient = new DefaultFacebookClient(authToken)
    		try{ 
    			val httpclient = new DefaultHttpClient()
    			val httpGet = new HttpGet("https://graph.facebook.com/me/permissions?access_token="+authToken)
    			val ermissionsRequestResponse = httpclient.execute(httpGet)
    			val responseJSON = play.api.libs.json.Json.parse(EntityUtils.toString(ermissionsRequestResponse.getEntity()))
    			
    			val permissionsList = (responseJSON \ "data").validate[List[JsObject]]
    			var i = 0
    			for(i <- 0 to permissionsList.asOpt.get.size-1){
    			  val currPermission = permissionsList.get(i)
    			  if((currPermission\"permission").asOpt[String].get == "publish_actions"){
    			    if((currPermission\"status").asOpt[String].get == "granted"){
    			      return true
    			    }
    			    else{
    			      return false
    			    }
    			  }
    			}
    			return false
    			
		    }catch{ case ex: Exception => {
		    	Logger.error(ex.toString())
        		Logger.error("Could not get permissions. Assuming failed authentication.")
        		false
        	}}    
  }
  
}
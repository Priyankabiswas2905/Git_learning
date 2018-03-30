package services

import play.api.{Application, Logger}
import play.api.Play.current
import javax.mail._
import javax.mail.internet._
import javax.activation._
import javax.inject.Inject

import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

trait MailService

class MailServiceImpl @Inject() (lifecycle: ApplicationLifecycle) extends MailService {
  Logger.debug("Starting Mailer Plugin")
   val from = play.Play.application().configuration().getString("smtp.from")  
   val host =  play.Play.application().configuration().getString("smtp.host")
   val properties = System.getProperties()
   properties.setProperty("mail.smtp.host", host)
   
   //SSL or regular SMTP
      var port = play.api.Play.configuration.getInt("smtp.port").getOrElse(0)
      if(play.api.Play.configuration.getBoolean("smtp.ssl").getOrElse(false)){
        if(port == 0)
          port = 465
          
        properties.setProperty("mail.smtp.socketFactory.port", port.toString)
        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")          
      }
      else{
        if(port == 0)
          port = 25
      }
      properties.setProperty("mail.smtp.port", port.toString)
    
      val user = play.api.Play.configuration.getString("smtp.user").getOrElse("")  
   
      if(!user.equals("")){
        properties.setProperty("mail.smtp.auth", "true")
      }

  lifecycle.addStopHook { () =>
    Logger.debug("Shutting down Mailer Plugin")
    Future.successful(())
  }

  lazy val enabled = {
    import play.api.Play.current
    !current.configuration.getString("mailservice").filter(_ == "disabled").isDefined
  }
  
  def sendMail(subscriberMail : String, html: String, subject: String): Boolean = {
		  
	  Logger.debug("Sending mail to " + subscriberMail)
          
      //Authenticate if needed
      var session = Session.getDefaultInstance(properties)     
      if(!user.equals("")){
        session = Session.getInstance(properties,
			  new javax.mail.Authenticator() {
				override def getPasswordAuthentication(): PasswordAuthentication = {
					return new PasswordAuthentication(user, play.api.Play.configuration.getString("smtp.password").getOrElse(""))
				}
			  })
      }
      
      try{
        val message = new MimeMessage(session)
        message.setFrom(new InternetAddress(from))
        message.addRecipient(Message.RecipientType.TO,
                                  new InternetAddress(subscriberMail))
        message.setSubject(subject)
        message.setContent(html, "text/html")
        Transport.send(message)
                                  
        Logger.debug("Sent message successfully.")
      }catch {
        case msgex: MessagingException =>{
        	Logger.error(msgex.toString())
        	return false
        }  
      }
      return true
  }
  

}
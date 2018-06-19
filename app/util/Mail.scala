package util

import akka.actor.ActorSystem
import javax.inject.Inject
import models.User
import play.api.libs.concurrent.Akka
import play.twirl.api.Html
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import services.{DI, UserService}

import scala.collection.immutable.Iterable
import scala.collection.mutable
import scala.concurrent.duration._
import play.api.Play.current
import play.api.libs.mailer._

/**
  * Helper functions for sending emails.
  */
object Mail {

  val actorSystem: ActorSystem = DI.injector.instanceOf[ActorSystem]

  /**
   * Send email to a single recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: String, body: Html) {
    sendEmail(subject, emailAddress(user), recipient::Nil, body)
  }

  /**
   * Send email to a single recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: Option[User], body: Html) {
    recipient.foreach(sendEmail(subject, user, _, body))
  }

  /**
   * Send email to a single recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: User, body: Html) {
    if (recipient.email.isDefined) {
      sendEmail(subject, emailAddress(user), emailAddress(Some(recipient))::Nil, body)
    }
  }

  /**
   * Send email to all server admins
   */
  def sendEmailAdmins(subject: String, user: Option[User], body: Html): Unit = {
    sendEmail(subject, emailAddress(user), getAdmins, body)
  }

  /**
   * Send email to all recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: Iterable[String], body: Html) {
    sendEmail(subject, emailAddress(user), recipient, body)
  }

  /**
   * Send email to all recipients
   */
  def sendEmail(subject: String, from: String, recipients: Iterable[String], body: Html) {
    // this needs to be done otherwise the request object is lost and this will throw an
    // error.
    val text = body.body
    if ( Logger.isDebugEnabled ) {
      Logger.debug("Sending email to %s".format(recipients.toList:_*))
      Logger.debug("Mail = [%s]".format(text))
    }
    actorSystem.scheduler.scheduleOnce(1.seconds) {
      val email = Email(subject, from, to=recipients.toSeq, bodyText=Some(text))
      MailerPlugin.send(email)
    }
  }

  private def getAdmins: List[String] = {
    val userService: UserService = DI.injector.instanceOf[UserService]

    val admins = mutable.ListBuffer[String]()
    val seen = mutable.HashSet[String]()
    for (x <- userService.getAdmins) {
      if (x.email.isDefined && !seen(x.email.get)) {
        admins += emailAddress(Some(x))
        seen += x.email.get
      }
    }
    admins.toList
  }

  private def emailAddress(user: Option[User]): String = {
    val from = current.configuration.getString("smtp.from").getOrElse("devnull@ncsa.illinois.edu")
    val name = current.configuration.getString("smtp.fromName").getOrElse("Clowder")
    user match {
      case Some(u) => {
        u.email match {
          case Some(e) => s""""${u.fullName.replace("\"", "")}" <${e}>"""
          case None => s""""${u.fullName.replace("\"", "")}" <${from}>"""
        }
      }
      case None => s""""${name.replace("\"", "")}" <${from}>"""
    }
  }
}

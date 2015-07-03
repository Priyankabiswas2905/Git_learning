import play.api.{GlobalSettings, Application}
import play.api.Logger

import play.filters.gzip.GzipFilter

import play.libs.Akka
import services.AppConfiguration
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import models.{ServerStartTime, CORSFilter, ExtractionInfoSetUp, JobsScheduler}
import java.util.Calendar
import play.api.mvc.WithFilters
import akka.actor.Cancellable
import julienrf.play.jsonp.Jsonp

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends WithFilters(new GzipFilter(), new Jsonp(), CORSFilter()) with GlobalSettings {
  var extractorTimer: Cancellable = null
  var jobTimer: Cancellable = null


  override def onStart(app: Application) {
    ServerStartTime.startTime = Calendar.getInstance().getTime
    Logger.debug("\n----Server Start Time----" + ServerStartTime.startTime + "\n \n")

    // set admins
    AppConfiguration.setDefaultAdmins()

    extractorTimer = Akka.system().scheduler.schedule(0 minutes, 5 minutes) {
      ExtractionInfoSetUp.updateExtractorsInfo()
    }

    // Use if Mailer Server and stmp in Application.conf are set up

    jobTimer = Akka.system().scheduler.schedule(0 minutes, 1 minutes) {
      JobsScheduler.runScheduledJobs()
    }

    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    extractorTimer.cancel()
    jobTimer.cancel()
    Logger.info("Application shutdown")
  }

  private lazy val injector = services.DI.injector

  /** Used for dynamic controller dispatcher **/
  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }
}

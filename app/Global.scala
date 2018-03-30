import java.io.{PrintWriter, StringWriter}
import java.util.Calendar

import akka.actor.Cancellable
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json._
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import play.api.{Application, GlobalSettings, Logger}
import play.libs.Akka
import services._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends GlobalSettings {
//  var extractorTimer: Cancellable = null
//  var jobTimer: Cancellable = null


  override def onStart(app: Application) {
//    val appConfig: AppConfigurationService = DI.injector.instanceOf[AppConfigurationService]
    val appConfig: AppConfigurationService = DI.injector.instanceOf[AppConfigurationService]

    ServerStartTime.startTime = Calendar.getInstance().getTime
    Logger.debug("\n----Server Start Time----" + ServerStartTime.startTime + "\n \n")

    val users: UserService = DI.injector.instanceOf[UserService]

    // set the default ToS version
    AppConfiguration.setDefaultTermsOfServicesVersion()

    // add all new admins
    users.updateAdmins()

    // create default roles
    if (users.listRoles().isEmpty) {
      Logger.debug("Ensuring roles exist")
      users.updateRole(Role.Admin)
      users.updateRole(Role.Editor)
      users.updateRole(Role.Viewer)
    }

    // set default metadata definitions
    MetadataDefinition.registerDefaultDefinitions()

//    if (extractorTimer == null) {
//      extractorTimer = Akka.system().scheduler.schedule(0 minutes, 5 minutes) {
//        ExtractionInfoSetUp.updateExtractorsInfo()
//      }
//    }
//
//    if (jobTimer == null) {
//      jobTimer = Akka.system().scheduler.schedule(0 minutes, 1 minutes) {
//        JobsScheduler.runScheduledJobs()
//      }
//    }

    // Get database counts from appConfig; generate them if unavailable or user count = 0
    appConfig.getProperty[Long]("countof.users") match {
      case Some(usersCount) =>
        Logger.debug("user counts found in appConfig; skipping database counting")
      case None => {
        // Write 0 to users count, so other instances can see this and not trigger additional counts
        appConfig.incrementCount('users, 0)

        Akka.system().scheduler.scheduleOnce(10 seconds) {
          Logger.debug("initializing appConfig counts")
          val datasets: DatasetService = DI.injector.instanceOf[DatasetService]
          val files: FileService = DI.injector.instanceOf[FileService]
          val collections: CollectionService = DI.injector.instanceOf[CollectionService]
          val spaces: SpaceService = DI.injector.instanceOf[SpaceService]
          val users: UserService = DI.injector.instanceOf[UserService]

          // Store the results in appConfig so they can be fetched quickly later
          appConfig.incrementCount('datasets, datasets.count())
          appConfig.incrementCount('files, files.count())
          appConfig.incrementCount('bytes, files.bytes())
          appConfig.incrementCount('collections, collections.count())
          appConfig.incrementCount('spaces, spaces.count())
          appConfig.incrementCount('users, users.count())
        }
      }
    }

    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
//    extractorTimer.cancel()
//    jobTimer.cancel()
    Logger.info("Application shutdown")
  }

//  private lazy val injector = services.DI.injector
//  private lazy val users: UserService =  DI.injector.instanceOf[UserService]

  /** Used for dynamic controller dispatcher **/
//  def getControllerInstance[A](clazz: Class[A]) = {
//    injector.getInstance(clazz)
//  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    ex.printStackTrace(pw)

    if (request.path.contains("/api/")) {
      Future(InternalServerError(toJson(Map("status" -> "error",
        "request" -> request.toString(),
        "exception" -> sw.toString.replace("\n", "\\n")))))
    } else {
      // TODO get identity from request
//      val users: UserService =  DI.injector.instanceOf[UserService]
//      implicit val user = users.findByIdentity("userId", "providerId")
      Future(InternalServerError(views.html.errorPage(request, sw.toString)(Some(User.anonymous))))
    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    if (request.path.contains("/api/")) {
      Future(InternalServerError(toJson(Map("status" -> "not found",
        "request" -> request.toString()))))
    } else {
      // TODO get idenitity from request
      val users: UserService = DI.injector.instanceOf[UserService]
      implicit val user = users.findByIdentity("userId", "providerId")
      Future(NotFound(views.html.errorPage(request, "Not found")(user)))
    }
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    if (request.path.contains("/api/")) {
      Future(InternalServerError(toJson(Map("status" -> "bad request",
        "message" -> error,
        "request" -> request.toString()))))
    } else {
      // TODO get identity from request
      val users: UserService = DI.injector.instanceOf[UserService]
      implicit val user = users.findByIdentity("userId", "providerId")
      Future(BadRequest(views.html.errorPage(request, error)(user)))
    }
  }

}

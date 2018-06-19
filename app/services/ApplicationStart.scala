package services

import java.util.Calendar

import akka.actor.{ActorSystem, Cancellable}
import javax.inject._
import models._
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

// This creates an `ApplicationStart` object once at start-up and registers hook for shut-down.
@Singleton
class ApplicationStart @Inject() (lifecycle: ApplicationLifecycle, actorSystem: ActorSystem) (implicit executionContext: ExecutionContext) {

  var extractorTimer: Cancellable = null
  var jobTimer: Cancellable = null

  // Shut-down hook
  lifecycle.addStopHook { () =>
    Future.successful({
      extractorTimer.cancel()
      jobTimer.cancel()
    })
  }
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

  if (extractorTimer == null) {
    extractorTimer = actorSystem.scheduler.schedule(0 minutes, 5 minutes) {
      ExtractionInfoSetUp.updateExtractorsInfo()
    }
  }

  if (jobTimer == null) {
    jobTimer = actorSystem.scheduler.schedule(0 minutes, 1 minutes) {
      JobsScheduler.runScheduledJobs()
    }
  }

  // Get database counts from appConfig; generate them if unavailable or user count = 0
  appConfig.getProperty[Long]("countof.users") match {
    case Some(usersCount) =>
      Logger.debug("user counts found in appConfig; skipping database counting")
    case None => {
      // Write 0 to users count, so other instances can see this and not trigger additional counts
      appConfig.incrementCount('users, 0)

      actorSystem.scheduler.scheduleOnce(10 seconds) {
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


}

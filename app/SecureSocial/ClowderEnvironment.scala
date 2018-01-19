package SecureSocial

import javax.inject.{Inject, Singleton}

import controllers.CustomRoutesService
import models.ClowderUser
import play.api.Configuration
import play.api.i18n.MessagesApi
import securesocial.core.RuntimeEnvironment

@Singleton
class ClowderEnvironment @Inject() (override val configuration: Configuration, override val messagesApi: MessagesApi) extends RuntimeEnvironment.Default {
  override type U = ClowderUser
  override implicit val executionContext = play.api.libs.concurrent.Execution.defaultContext
  override lazy val routes = new CustomRoutesService(configuration)
  override lazy val userService: InMemoryUserService = new InMemoryUserService()
  override lazy val eventListeners = List(new ClowderEventListener())
}

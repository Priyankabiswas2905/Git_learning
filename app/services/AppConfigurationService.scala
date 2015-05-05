package services

import play.Logger
import util.ResourceLister

/**
 * Application wide configuration options. This class contains the service definition
 * and can be used to store application configuration options. See also AppConfiguration
 * for specific configuration options.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 */
trait AppConfigurationService {
  /** Adds an additional value to the property with the specified key. */
  def addPropertyValue(key: String, value: AnyRef)

  /** Removes the value from the property with the specified key. */
  def removePropertyValue(key: String, value: AnyRef)

  /** Checks to see if the value is part of the property with the specified key. */
  def hasPropertyValue(key: String, value: AnyRef): Boolean

  /**
   * Gets the configuration property with the specified key. If the key is not found
   * it wil return None.
   */
  def getProperty[objectType <: AnyRef](key: String): Option[objectType]

  /**
   * Gets the configuration property with the specified key. If the key is not found
   * it wil return the default value (empty string if not specified).
   */
  def getProperty[objectType <: AnyRef](key: String, default:objectType): objectType = {
    getProperty[objectType](key) match {
      case Some(x) => x
      case None => default
    }
  }

  /**
   * Sets the configuration property with the specified key to the specified value. If the
   * key already existed it will return the old value, otherwise it returns None.
   */
  def setProperty(key: String, value: AnyRef): Option[AnyRef]

  /**
   * Remove the configuration property with the specified key and returns the value if any
   * was set, otherwise it will return None.
   */
  def removeProperty(key: String): Option[AnyRef]
}

/**
 * Object to handle some common configuration options.
 */
object AppConfiguration {
  val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

  // ----------------------------------------------------------------------

  /** Set the default theme */
  def setTheme(theme: String) = appConfig.setProperty("theme", theme)

  /** Get the default theme */
  def getTheme: String = appConfig.getProperty[String]("theme", "simplex.min.css")

  /** Get list of available themes */
  def themes: List[String] = {
    ResourceLister.listFiles("public.stylesheets.themes", ".*.css")
      .map(s => s.replaceAll(".*.themes.", ""))
  }

  // ----------------------------------------------------------------------

  /** Set the display name (subtitle) */
  def setDisplayName(displayName: String) = appConfig.setProperty("display.name", displayName)

  /** Get the display name (subtitle) */
  def getDisplayName: String = appConfig.getProperty("display.name", "Medici 2.0")

  // ----------------------------------------------------------------------

  /** Set the welcome message */
  def setWelcomeMessage(welcomeMessage: String) = appConfig.setProperty("welcome.message", welcomeMessage)

  /** Get the welcome message */
  def getWelcomeMessage: String = appConfig.getProperty("welcome.message", "Welcome to Medici 2.0, " +
    "a scalable data repository where you can share, organize and analyze data.")

  // ----------------------------------------------------------------------

  /**
   * Add the given admin to list of admins. This list is primarily used when a new user signs
   * up (requires registerThroughAdmins to be set to true in application.conf) or when the
   * plugin is enabled to send emails on creating of new datasets, collections and/or files.
   */
  def addAdmin(admin: String) = appConfig.addPropertyValue("admins", admin)

  /**
   * Removes the given admin to list of admins. This list is primarily used when a new user signs
   * up (requires registerThroughAdmins to be set to true in application.conf) or when the
   * plugin is enabled to send emails on creating of new datasets, collections and/or files.
   */
  def removeAdmin(admin: String) = appConfig.removePropertyValue("admins", admin)

  /**
   * Checks if the given admin is on the list of admins. This list is primarily used when a
   * new user signs up (requires registerThroughAdmins to be set to true in application.conf)
   * or when the plugin is enabled to send emails on creating of new datasets, collections
   * and/or files.
   */
  def checkAdmin(admin: String) = appConfig.hasPropertyValue("admins", admin)

  /**
   * Get list of all admins. This list is primarily used when a new user signs up (requires
   * registerThroughAdmins to be set to true in application.conf) or when the plugin is enabled
   * to send emails on creating of new datasets, collections and/or files.
   */
  def getAdmins: List[String] = appConfig.getProperty[List[String]]("admins", List.empty[String])

  /**
   * Sets default admins as specified in application.conf. This list is primarily used when a new
   * user signs up (requires registerThroughAdmins to be set to true in application.conf) or when
   * the plugin is enabled to send emails on creating of new datasets, collections and/or files.
   */
  def setDefaultAdmins() = {
    if (!appConfig.getProperty[List[String]]("admins").isDefined) {
      val x = play.Play.application().configuration().getString("initialAdmins")
      if (x != "") {
        appConfig.setProperty("admins", x.trim.split("\\s*,\\s*").toList)
      }
    }
  }
}

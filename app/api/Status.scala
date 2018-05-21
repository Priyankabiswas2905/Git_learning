package api

import javax.inject.Inject
import play.api.{Configuration, Logger}
import models.User
import play.api.Play._
import play.api.libs.json.{JsValue, Json}
import services._
import services.mongodb.{MongoSalatPlugin, MongoService}

import scala.collection.mutable

/**
 * class that contains all status/version information about clowder.
 */
class Status @Inject()(spaces: SpaceService,
  collections: CollectionService,
  datasets: DatasetService,
  files: FileService,
  users: UserService,
  appConfig: AppConfigurationService,
  extractors: ExtractorService,
  geostreamsService: GeostreamsService,
  elasticsearchService: ElasticsearchService,
  mongoService: MongoService,
  rabbitMQService: RabbitMQService,
  toolManagerService: ToolManagerService, conf: Configuration) extends ApiController {

  val jsontrue = Json.toJson(true)
  val jsonfalse = Json.toJson(false)

  def version = UserAction(needActive=false) { implicit request =>
    Ok(Json.obj("version" -> getVersionInfo))
  }

  def status = UserAction(needActive=false) { implicit request =>

    Ok(Json.obj("version" -> getVersionInfo,
      "counts" -> getCounts(request.user),
      "plugins" -> getPlugins(request.user),
      "extractors" -> Json.toJson(extractors.getExtractorNames())))
  }

  def getPlugins(user: Option[User]): JsValue = {
    val result = new mutable.HashMap[String, JsValue]()

    // geostream
    if (current.configuration.getBoolean("geostream.enabled").getOrElse(false)) {
      val conn = geostreamsService.conn
      val status = if (conn != null) {
        "connected"
      } else {
        "disconnected"
      }
      result.put("postgres", if (Permission.checkServerAdmin(user)) {
        Json.obj("catalog" -> conn.getCatalog,
          "schema" -> conn.getSchema,
          "updates" -> appConfig.getProperty[List[String]]("postgres.updates", List.empty[String]),
          "status" -> status)
      } else {
        Json.obj("status" -> status)
      })
    }

    // elasticsearch
    if (current.configuration.getBoolean("geostream.enabled").getOrElse(false)) {
      val status = if (elasticsearchService.isEnabled()) {
        "connected"
      } else {
        "disconnected"
      }
      result.put("elasticsearch", if (Permission.checkServerAdmin(user)) {
        Json.obj("server" -> elasticsearchService.serverAddress,
          "clustername" -> elasticsearchService.nameOfCluster,
          "status" -> status)
      } else {
        Json.obj("status" -> status)
      })
    }

    // versus
    if (conf.get[Boolean]("versus.enabled")) {
      result.put("versus", if (Permission.checkServerAdmin(user)) {
        Json.obj("host" -> conf.get[String]("versus.host"))
      } else {
        jsontrue
      })
    }

      // mongo
      result.put("mongo", if (Permission.checkServerAdmin(user)) {
        Json.obj("uri" -> mongoService.mongoURI.toString(),
          "updates" -> appConfig.getProperty[List[String]]("mongodb.updates", List.empty[String]))
      } else {
        jsontrue
      })

      // rabbitmq
    if (current.configuration.getBoolean("clowder.rabbitmq.enabled").getOrElse(false)) {
        val status = if (rabbitMQService.connect) {
          "connected"
        } else {
          "disconnected"
        }
        result.put("rabbitmq", if (Permission.checkServerAdmin(user)) {
          Json.obj("uri" -> rabbitMQService.rabbitmquri,
            "exchange" -> rabbitMQService.exchange,
            "status" -> status)
        } else {
          Json.obj("status" -> status)
        })
      }

    if (current.configuration.getBoolean("clowder.toolmanager.enabled").getOrElse(false)) {
        val status = if (toolManagerService.enabled) {
          "enabled"
        } else {
          "disabled"
        }
        result.put("toolmanager", if (Permission.checkServerAdmin(user)) {
          Json.obj("host" -> conf.get[String]("toolmanagerURI").getOrElse("").toString,
            "tools" -> toolManagerService.getLaunchableTools(),
            "status" -> status)
        } else {
          Json.obj("status" -> status)
        })
      }
    Json.toJson(result.toMap[String, JsValue])
  }

  def getCounts(user: Option[User]): JsValue = {
    val counts = appConfig.getIndexCounts()
    // TODO: Revisit this check as it is currently too slow
    //val fileinfo = if (Permission.checkServerAdmin(user)) {
    //  Json.toJson(files.statusCount().map{x => x._1.toString -> Json.toJson(x._2)})
    //} else {
    //  Json.toJson(counts.numFiles)
    //}
    val fileinfo = counts.numFiles
    Json.obj("spaces" -> counts.numSpaces,
      "collections" -> counts.numCollections,
      "datasets" -> counts.numDatasets,
      "files" -> fileinfo,
      "bytes" -> counts.numBytes,
      "users" -> counts.numUsers)
  }

  def getVersionInfo: JsValue = {
    val sha1 = sys.props.getOrElse("build.gitsha1", default = "unknown")

    // TODO use the following URL to indicate if there updates to clowder.
    // if returned object has an empty values clowder is up to date
    // need to figure out how to pass in the branch
    //val checkurl = "https://opensource.ncsa.illinois.edu/stash/rest/api/1.0/projects/CATS/repos/clowder/commits?since=" + sha1

    Json.obj("number" -> sys.props.getOrElse("build.version", default = "0.0.0").toString,
      "build" -> sys.props.getOrElse("build.bamboo", default = "development").toString,
      "branch" -> sys.props.getOrElse("build.branch", default = "unknown").toString,
      "gitsha1" -> sha1)
  }
}

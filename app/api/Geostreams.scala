/**
 *
 */
package api

import play.api.mvc.Action
import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._
import play.api.Play.current
import java.text.SimpleDateFormat
import play.api.Logger
import java.sql.Timestamp
import services.PostgresPlugin
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListMap
import scala.collection.mutable.ListBuffer
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Geostreaming endpoints. A geostream is a time and geospatial referenced
 * sequence of datapoints.
 *
 * @author Luigi Marini
 *
 */
object Geostreams extends ApiController {

  val formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssXXX")
  val pluginNotEnabled = InternalServerError(toJson("Geostreaming not enabled"))
  
  implicit val sensors = (
    (__ \ 'name).read[String] and
    (__ \ 'type).read[String] and
    (__ \ 'geometry \ 'coordinates).read[List[Double]] and
    (__ \ 'properties).read[JsValue]
  ) tupled
  
  implicit val streams = (
    (__ \ 'name).read[String] and
    (__ \ 'type).read[String] and
    (__ \ 'geometry \ 'coordinates).read[List[Double]] and
    (__ \ 'properties).read[JsValue] and
    (__ \ 'sensor_id).read[String]
  ) tupled
  
  implicit val datapoints = (
    (__ \ 'start_time).read[String] and
    (__ \ 'end_time).readNullable[String] and
    (__ \ 'type).read[String] and
    (__ \ 'geometry \ 'coordinates).read[List[Double]] and
    (__ \ 'properties).json.pick and
    (__ \ 'stream_id).read[String]
  ) tupled

  def createSensor() = SecuredAction(authorization=WithPermission(Permission.CreateSensors)) { request =>
      Logger.debug("Creating sensor")
      request.body.validate[(String, String, List[Double], JsValue)].map {
        case (name, geoType, longlat, metadata) => {
          current.plugin[PostgresPlugin] match {
            case Some(plugin) => {
              plugin.createSensor(name, geoType, longlat(1), longlat(0), longlat(2), Json.stringify(metadata))
              Ok(toJson("success"))
            }
            case None => pluginNotEnabled
          }
        }
      }.recoverTotal {
        e => BadRequest("Detected error:" + JsError.toFlatJson(e))
      }
  }

  def searchSensors(geocode: Option[String]) =
    Action { request =>
      Logger.debug("Searching sensors " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.searchSensors(geocode) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  def getSensor(id: String) =
    Action { request =>
      Logger.debug("Get sensor " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.getSensor(id) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  
  def getSensorStreams(id: String) =
    Action { request =>
      Logger.debug("Get sensor streams" + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.getSensorStreams(id) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  def updateStatisticsSensor(id: String) = 
    Action { request =>
      Logger.debug("update sensor statistics for " + id)
	  current.plugin[PostgresPlugin] match {
	    case Some(plugin) => {
	      plugin.updateSensorStats(Some(id))
	      Ok(Json.parse("""{"status":"updated"}""")).as("application/json")
	    }
	    case None => pluginNotEnabled
	  }
  }
  
  def updateStatisticsStream(id: String) = 
    Action { request =>
      Logger.debug("update stream statistics for " + id)
	  current.plugin[PostgresPlugin] match {
	    case Some(plugin) => {
	      plugin.updateStreamStats(Some(id))
	      Ok(Json.parse("""{"status":"updated"}""")).as("application/json")
	    }
	    case None => pluginNotEnabled
	  }
  }
  
  def updateStatisticsStreamSensor() = 
    Action { request =>
      Logger.debug("update all sensor/stream statistics")
	  current.plugin[PostgresPlugin] match {
	    case Some(plugin) => {
	      plugin.updateSensorStats(None)
	      Ok(Json.parse("""{"status":"updated"}""")).as("application/json")
	    }
	    case None => pluginNotEnabled
	  }
  }
  
  def getSensorStatistics(id: String) =  
    Action { request =>
      Logger.debug("Get sensor statistics " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          val json = plugin.getSensorStats(id) match {
            case Some(d) => {
              val data = Json.parse(d)
              Json.obj(
                  "range" -> Map[String, JsValue]("min_start_time" -> data \ "min_start_time", "max_start_time" -> data \ "max_start_time"),
                  "parameters" -> data \ "parameters"
                  )
            }
            case None => Json.obj("range" -> Map.empty[String, String], "parameters" -> Array.empty[String])
          }
          Ok(jsonp(Json.prettyPrint(json), request)).as("application/json")
        }
        case None => pluginNotEnabled
      }
  }
  
  def createStream() = SecuredAction(authorization=WithPermission(Permission.CreateSensors)) { request =>
      Logger.info("Creating stream: " + request.body)
      request.body.validate[(String, String, List[Double], JsValue, String)].map {
        case (name, geoType, longlat, metadata, sensor_id) => {
          current.plugin[PostgresPlugin] match {
            case Some(plugin) => {
              val id = plugin.createStream(name, geoType, longlat(1), longlat(0), longlat(2), Json.stringify(metadata), sensor_id)
              Ok(toJson(Json.obj("status"->"ok","id"->id)))              
            }
            case None => pluginNotEnabled
          }
        }
      }.recoverTotal {
        e => Logger.error(e.toString); BadRequest("Detected error:" + JsError.toFlatJson(e))
      }
  }

  def searchStreams(geocode: Option[String]) =
    Action { request =>
      Logger.debug("Searching stream " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.searchStreams(geocode) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  def getStream(id: String) =
    Action { request =>
      Logger.debug("Get stream " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.getStream(id) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No stream found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  def deleteStream(id: String) = Authenticated {
    Action(parse.empty) { request =>
      Logger.debug("Delete stream " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          if (plugin.deleteStream(id.toInt)) Ok(Json.parse("""{"status":"ok"}""")).as("application/json")
          else Ok(Json.parse("""{"status":"error"}""")).as("application/json")
        }
        case None => pluginNotEnabled
      }
    }
  }
  
  def deleteAll() = Authenticated {
    Action(parse.empty) { request =>
      Logger.debug("Drop all")
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          if (plugin.dropAll()) Ok(Json.parse("""{"status":"ok"}""")).as("application/json")
          else Ok(Json.parse("""{"status":"error"}""")).as("application/json")
        }
        case None => pluginNotEnabled
      }
    }
  }
  
  def counts() =  Action { request =>
      Logger.debug("Counting entries")
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.counts() match {
          	case (sensors, streams, datapoints) => Ok(toJson(Json.obj("sensors"->sensors,"streams"->streams,"datapoints"->datapoints))).as("application/json")
          	case _ => Ok(Json.parse("""{"status":"error"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }

  def addDatapoint() = Authenticated {
    Action(parse.json) { request =>
      Logger.info("Adding datapoint: " + request.body)
      request.body.validate[(String, Option[String], String, List[Double], JsValue, String)].map {
        case (start_time, end_time, geoType, longlat, data, streamId) =>
          current.plugin[PostgresPlugin] match {
            case Some(plugin) => {
              Logger.info("GEOSTREAM TIME: " + start_time + " " + end_time)
              val start_timestamp = new Timestamp(formatter.parse(start_time).getTime())
              val end_timestamp = if (end_time.isDefined) Some(new Timestamp(formatter.parse(end_time.get).getTime())) else None
              if (longlat.length == 3) {
                plugin.addDatapoint(start_timestamp, end_timestamp, geoType, Json.stringify(data), longlat(1), longlat(0), longlat(2), streamId)
              } else {
                plugin.addDatapoint(start_timestamp, end_timestamp, geoType, Json.stringify(data), longlat(1), longlat(0), 0.0, streamId)
              }
              Ok(toJson("success"))
            }
            case None => pluginNotEnabled
          }
      }.recoverTotal {
        e => Logger.debug("Error parsing json: " + e); BadRequest("Detected error:" + JsError.toFlatJson(e))
      }
    }
  }

  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sources: List[String], attributes: List[String], format: String) =
    Action { request =>
      Logger.debug("Search " + since + " " + until + " " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.searchDatapoints(since, until, geocode, stream_id, sources, attributes) match {
            case Some(d) => {
              if (format == "csv") {
                val configuration = play.api.Play.configuration
                val hideprefix = configuration.getBoolean("json2csv.hideprefix").getOrElse(false)
                val ignore = configuration.getString("json2csv.ignore").getOrElse("").split(",")
                val seperator = configuration.getString("json2csv.seperator").getOrElse("|")
                val fixgeometry = configuration.getBoolean("json2csv.fixgeometry").getOrElse(true)
                Ok.chunked(jsonToCSV(Json.parse(d), ignore, seperator, hideprefix, fixgeometry)).as("text/csv")
              } else {
                Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
              }
            }
            case None => Ok(Json.toJson("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }

  def getDatapoint(id: String) =
    Action { request =>
      Logger.debug("Get datapoint " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.getDatapoint(id) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No stream found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }

  def jsonp(json: String, request: Request[AnyContent]) = {
    request.getQueryString("callback") match {
      case Some(callback) => callback + "(" + json + ");"
      case None => json
    }
  }

  // ----------------------------------------------------------------------
  // Convert JSON data to CSV data.
  // This code is generic and could be moved to special module, right now
  // it is used to take a geostream output and convert it to CSV.
  // ----------------------------------------------------------------------  
  /**
   * Returns the JSON formated as CSV.
   *
   * This will take the first json element and create a header from this. All
   * additional rows will be parsed based on this first header. Any fields not
   * in the first row, will not be outputed.
   * @param data the json to be converted to CSV.
   * @param ignore fields to ignore.
   * @param prefixSeperator set this to the value to seperate elements in json
   * @param hidePrefix set this to true to not print the prefix in header.
   * @param fixGeometry set this to true to convert an array of geomtry to lat, lon, alt
   * @param 
   * @returns Enumarator[String]
   */
  def jsonToCSV(data: JsValue, ignore: Array[String] = Array[String](), prefixSeperator: String = " - ", hidePrefix: Boolean = false, fixGeometry:Boolean = true) = {
    val values = data.as[List[JsObject]]
    val headers = ListBuffer.empty[Header]

    // find all headers
    var rowcount = 0
    val runtime = Runtime.getRuntime()
    
    Enumerator.generateM(Future[Option[String]] {
      if (headers.isEmpty) {
	    for(row <- values)
	      addHeaders(row, headers, ignore, "", prefixSeperator, hidePrefix, fixGeometry)
        Some(printHeader(headers).substring(1) + "\n")
      } else if (rowcount < values.length) {
        val x = Some(printRow(values(rowcount), headers, "", prefixSeperator) + "\n")
        rowcount += 1
        x
      } else {
        None
      }
    })
  }

  /**
   * Helper function to create a new prefix based on the key, and current prefix
   */
  def getPrefix(key: Any, prefix: String, prefixSeperator: String) = {
    if (prefix == "")
       key.toString
     else
       prefix + prefixSeperator + key.toString
  }

  /**
   * Helper function to recursively print the header
   */
  def printHeader(headers: ListBuffer[Header]):String = {
    var result = ""
    for(h <- headers) {
      h.value match {
        case Left(x) => result =result + ",\"" + x + "\""
        case Right(x) => result = result + printHeader(x)
      }
    }
    result
  }
  
  /**
   * Helper function to create list of headers
   */
  def addHeaders(row: JsObject, headers: ListBuffer[Header], ignore: Array[String], prefix: String, prefixSeperator: String, hidePrefix: Boolean, fixGeometry: Boolean) {
    for(f <- row.fields if !(ignore contains getPrefix(f._1, prefix, prefixSeperator))) {
      f._2 match {
        case y: JsArray => {
          headers.find(x => x.key.equals(f._1)) match {
            case Some(Header(f._1, Left(x))) => Logger.error("Duplicate key [" + f._1 + "] detected")
            case Some(Header(f._1, Right(x))) => addHeaders(y, x, ignore, getPrefix(f._1, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            case Some(x) => Logger.error("Unknown header found : " + x)
            case None => {
              val x = ListBuffer.empty[Header]
              headers += Header(f._1, Right(x))
              addHeaders(y, x, ignore, getPrefix(f._1, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            }
          }
        }
        case y: JsObject => {
          headers.find(x => x.key.equals(f._1)) match {
            case Some(Header(f._1, Left(x))) => Logger.error("Duplicate key [" + f._1 + "] detected")
            case Some(Header(f._1, Right(x))) => addHeaders(y, x, ignore, getPrefix(f._1, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            case Some(x) => Logger.error("Unknown header found : " + x)
            case None => {
              val x = ListBuffer.empty[Header]
              headers += Header(f._1, Right(x))
              addHeaders(y, x, ignore, getPrefix(f._1, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            }
          }
        }
        case y => {
          headers.find(x => x.key.equals(f._1)) match {
            case Some(Header(f._1, Left(x))) => None
            case Some(Header(f._1, Right(x))) => Logger.error("Duplicate key [" + f._1 + "] detected")
            case _ => headers += Header(f._1, Left(getHeader(f._1, prefix, prefixSeperator, hidePrefix, fixGeometry)))
          }
        }
      }
    }
  }
  
  /**
   * Helper function to create list of headers
   */
  def addHeaders(row: JsArray, headers: ListBuffer[Header], ignore: Array[String], prefix: String, prefixSeperator: String, hidePrefix: Boolean, fixGeometry: Boolean) {
    row.value.indices.withFilter(i => !(ignore contains getPrefix(i, prefix, prefixSeperator))).foreach(i => {
      val s = i.toString
      row(i) match {
        case y: JsArray => {
          headers.find(f => f.key.equals(s)) match {
            case Some(Header(s, Left(x))) => Logger.error("Duplicate key [" + s + "] detected")
            case Some(Header(s, Right(x))) => addHeaders(y, x, ignore, getPrefix(i, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            case None => {
              val x = ListBuffer.empty[Header]
              headers += Header(s, Right(x))
              addHeaders(y, x, ignore, getPrefix(i, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            }
          }
        }
        case y: JsObject => {
          headers.find(f => f.key.equals(s)) match {
            case Some(Header(s, Left(x))) => Logger.error("Duplicate key [" + s + "] detected")
            case Some(Header(s, Right(x))) => addHeaders(y, x, ignore, getPrefix(i, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            case None => {
              val x = ListBuffer.empty[Header]
              headers += Header(s, Right(x))
              addHeaders(y, x, ignore, getPrefix(i, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            }
          }
        }
        case y => {
          headers.find(f => f.key.equals(s)) match {
            case Some(Header(s, Left(x))) => None
            case Some(Header(s, Right(x))) => Logger.error("Duplicate key [" + s + "] detected")
            case _ => headers += Header(s, Left(getHeader(i, prefix, prefixSeperator, hidePrefix, fixGeometry)))
          }
        }
      }
    })
  }

  /**
   * Helper function to create the text that is printed as header
   */
  def getHeader(key: Any, prefix: String, prefixSeperator: String, hidePrefix: Boolean, fixGeometry: Boolean): String = {
    if (fixGeometry && prefix.endsWith("geometry" + prefixSeperator + "coordinates")) {
      (key.toString, hidePrefix) match {
        case ("0", true) => "longitude"
        case ("0", false) => getPrefix("longitude", prefix, prefixSeperator)
        case ("1", true) => "latitude"
        case ("1", false) => getPrefix("latitude", prefix, prefixSeperator)
        case ("2", true) => "altitude"
        case ("2", false) => getPrefix("altitude", prefix, prefixSeperator)
        case (_, true) => key.toString
        case (_, false) => getPrefix(key, prefix, prefixSeperator)
      }
    } else {
      if (hidePrefix)
        key.toString
      else
        getPrefix(key, prefix, prefixSeperator)
    }
  }

  /**
   * Helper function to print data row of JSON Object.
   */
  def printRow(row: JsObject, headers: ListBuffer[Header], prefix: String, prefixSeperator: String) : String = {
    var result = ""
    for(h <- headers) {
      (row.\(h.key), h.value) match {
        case (x: JsArray, Right(y)) => result += "," + printRow(x, y, getPrefix(h.key, prefix, prefixSeperator), prefixSeperator)
        case (x: JsObject, Right(y)) => result += "," + printRow(x, y, getPrefix(h.key, prefix, prefixSeperator), prefixSeperator)
        case (x: JsUndefined, Left(_)) => result += ","
        case (x, Right(y)) => result += "," + printRow(JsObject(Seq.empty), y, getPrefix(h.key, prefix, prefixSeperator), prefixSeperator)
        case (x, Left(_)) => result += "," + x
      }
    }
    result.substring(1)
  }

  /**
   * Helper function to print data row of JSON Array.
   */
  def printRow(row: JsArray, headers: ListBuffer[Header], prefix: String, prefixSeperator: String) : String  = {
    var result = ""
    for(h <- headers) {
      val i = h.key.toInt
      (row(i), h.value) match {
        case (x: JsArray, Right(y)) => result += "," + printRow(x, y, getPrefix(prefix, h.key, prefixSeperator), prefixSeperator)
        case (x: JsObject, Right(y)) => result += "," + printRow(x, y, getPrefix(prefix, h.key, prefixSeperator), prefixSeperator)
        case (x: JsUndefined, Left(_)) => result += ","
        case (x, Right(y)) => result += "," + printRow(JsObject(Seq.empty), y, getPrefix(h.key, prefix, prefixSeperator), prefixSeperator)
        case (x, Left(y)) => result += "," + x
      }
    }
    result.substring(1)
  }

  /**
   * Class to hold a json key, and any subkeys
   */
  case class Header(key: String, value: Either[String, ListBuffer[Header]])
}

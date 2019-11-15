package services.mongodb

import services.ExtractionService
import models.{Extraction, ExtractionGroup, ResourceRef, UUID}
import org.bson.types.ObjectId
import play.api.Play.current
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject
import java.util.Date

import play.api.Logger
import models.WebPageResource
import com.mongodb.casbah.Imports._

import scala.collection.SortedMap

/**
 * Use MongoDB to store extractions
 */
class MongoDBExtractionService extends ExtractionService {

  def findIfBeingProcessed(fileId: UUID): Boolean = {
    val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
    val extractorsArray: collection.mutable.Map[String, String] = collection.mutable.Map()
    for (currentExtraction <- allOfFile) {
      extractorsArray(currentExtraction.extractor_id) = currentExtraction.message_type
    }
    return extractorsArray.values.exists(statusString => !(statusString == "SUCCEEDED" || statusString.contains("ERROR")))
  }

  def findAll(max: Int): List[Extraction] = {
    Extraction.findAll().limit(max).toList
  }

  def findById(resource: ResourceRef): List[Extraction] = {
    Extraction.find(MongoDBObject("file_id" -> new ObjectId(resource.id.stringify))).toList
  }

  def insert(extraction: Extraction): Option[ObjectId] = {
    Extraction.insert(extraction)
  }
  
  /**
   * Returns list of extractors and their corresponding status for a specified file
   */
  
  def getExtractorList(fileId:UUID):collection.mutable.Map[String,String] = {
    val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
	var extractorsArray:collection.mutable.Map[String,String] = collection.mutable.Map()
	for(currentExtraction <- allOfFile){
	  extractorsArray(currentExtraction.extractor_id) = currentExtraction.message_type
	}
    return extractorsArray
  }

  def getExtractionTime(fileId:UUID):List[Date] ={
  val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
	var extractorsTimeArray=List[Date]()
	for(currentExtraction <- allOfFile){
	    extractorsTimeArray = currentExtraction.start.get :: extractorsTimeArray
	}
  return extractorsTimeArray
}

  def save(webpr:WebPageResource):UUID={
    WebPageResource.insert(webpr,WriteConcern.Safe)
    webpr.id
  }
  
  def getWebPageResource(id: UUID): Map[String,String]={
    val wpr=WebPageResource.findOne(MongoDBObject("_id"->new ObjectId(id.stringify)))
    var wprlist= wpr.map{
      e=>Logger.debug("resource id:" + id.toString)
         e.URLs
    }.getOrElse(Map.empty)
    wprlist         
  }

  // Return a mapping of ExtractorName -> (FirstMsgTime, LatestMsgTime, LatestMsg, ListOfAllMessages)
  def groupByType(extraction_list: List[Extraction]): Map[String, SortedMap[String, ExtractionGroup]] = {
    val format = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")

    // Map ExtractorName to a list of start_times for that extractor
    var groupings = Map[String, SortedMap[String, ExtractionGroup]]()
    for (e <- extraction_list) {
      if (e.message_type == "STARTED") {
        groupings.get(e.extractor_id) match {
          case Some(start_time_map) => {
            // append new start time
            val start = e.start match {
              case Some(s) => s.toString
              case None => "N/A"
            }
            val initial_group = ExtractionGroup(start, start, e.message_type, List(e))
            groupings = groupings + (e.extractor_id -> (start_time_map + (start -> initial_group)))
          }
          case None => {
            // add new entry
            val start = e.start match {
              case Some(s) => s.toString
              case None => "N/A"
            }
            val start_time_map = SortedMap(start -> ExtractionGroup(start, start, e.message_type, List(e)))
            groupings = groupings + (e.extractor_id -> start_time_map)
          }
        }
      }
    }


    // Populate the maps of start_time -> extraction events for rest of messages
    for (e <- extraction_list) {
      if (e.message_type != "STARTED") {
        // Find appropriate time bucket to put this event into
        e.start match {
          case Some(msg_time) => {
            groupings.get(e.extractor_id) match {
              case Some(start_time_map) => {
                var recent_start: Date = format.parse("Sun Jan 01 00:00:01 CST 1950")
                start_time_map.keys.foreach(start_time => {
                  val start_dt = format.parse(start_time)
                  if ((start_dt.before(msg_time) || start_dt.equals(msg_time)) && start_dt.after(recent_start)) {
                    recent_start = start_dt
                  }
                })
                // Found the bucket, so update the time ranges and list of events
                start_time_map.get(recent_start.toString) match {
                  case Some(event_group) => {
                    val current_events = event_group.allMsgs
                    var current_latest = format.parse(event_group.latestMsgTime)
                    val latest_msg = if (current_latest.before(msg_time)) {
                      current_latest = msg_time
                      e.message
                    } else {
                      event_group.latestMsg
                    }

                    groupings = groupings + (e.extractor_id -> (start_time_map + (recent_start.toString ->
                      ExtractionGroup(recent_start.toString, current_latest.toString, latest_msg, current_events :+ e))))
                  }
                  case None => Logger.error(s"For message at time ${msg_time.toString} no bin found at ${recent_start.toString}")
                }
              }
              case None => {
                val start = e.start match {
                  case Some(s) => s.toString
                  case None => "N/A"
                }
                val start_time_map = SortedMap(start -> ExtractionGroup(start, start, e.message_type, List(e)))
                groupings = groupings + (e.extractor_id -> start_time_map)
              }
            }
          }
          case None => Logger.error("Cannot display extractor message without a timestamp")
        }
      }
    }

    groupings
  }
}

object Extraction extends ModelCompanion[Extraction, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Extraction, ObjectId](collection = x.collection("extractions")) {}
  }
}

object WebPageResource extends ModelCompanion[WebPageResource,ObjectId]{
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[WebPageResource, ObjectId](collection = x.collection("webpage.resources")) {}
  }
}


package models

import java.net.URL
import java.util.Date
import play.api.libs.json.JsObject
import util.SpaceConfig

/**
 * A space definines a partition of the data so that authorization can be enforced on it.
 * It is also a way for users to create collaboration spaces with other users.
 *
 * @author Luigi Marini
 *
 */

case class ProjectSpace (
  id: UUID = UUID.generate,
  name: String = "N/A",
  description: String = "N/A",
  created: Date,
  creator: UUID, // attribution:UUID ?
  homePage: List[URL],
  logoURL: Option[URL],
  bannerURL: Option[URL],
  collectionCount: Integer,
  datasetCount: Integer,
  userCount: Integer,
  metadata: List[Metadata], 
  resourceTimeToLive: Long = SpaceConfig.getTimeToLive(),
  isTimeToLiveEnabled: Boolean = SpaceConfig.getIsTimeToLiveEnabled())


case class UserSpace (
   id: UUID = UUID.generate,
   name: String = "N/A",
   description: String = "N/A",
   homePage: List[URL],
   logoURL: Option[URL],
   bannerURL: Option[URL],
   collectionCount: Integer,
   datasetCount: Integer,
   userCount: Integer)

case class Role(
   id: UUID = UUID.generate,
   name: String = "Default")

object Role {
    val roleList: List[String] = List("Admin", "Power User", "User", "Guest")
    var roleMap: Map[String, Role] = Map.empty
    for (aRole <- roleList) {
        roleMap += (aRole -> Role(name = aRole))
    }
}   
   
// New way to manage metadata. Will eventually be merged with space metadata.
case class SpaceMetadata (
  created: Date,
  creator: Agent,     // user/extractor/tool
  content: JsObject,
  previousVersion: Option[UUID]
)

// Attempt at a generic provenance object. **Not ready**
case class ProvObj (
  typeofIf: String,
  id: UUID, // id of original object
  archived: Date,
  obj: JsObject)

// Generic Agent
trait Agent {
  val id: UUID
}

// User through the GUI
case class UserAgent(id: UUID, userId: Option[URL]) extends Agent

// Automatic extraction
case class ExtractorAgent(id: UUID, extractorId: Option[URL]) extends Agent

// User submitting tool
case class ToolAgent(id: UUID, toolId: Option[URL]) extends Agent
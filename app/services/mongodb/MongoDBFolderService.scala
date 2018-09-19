package services.mongodb

import models._
import services._
import play.api.Play.current
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import services.mongodb.MongoContext.context

/**
 * Use Mongodb to store folders
 */
@Singleton
class MongoDBFolderService @Inject() (files: FileService, datasets: DatasetService) extends FolderService{

  /**
   * Get Folder
   */
  def get(id: UUID): Option[Folder] = {
    FolderDAO.findOneById(new ObjectId(id.stringify))
  }

  /**
   * Create a Folder
   */
  def insert(folder: Folder): Option[String] = {

    FolderDAO.insert(folder).map(_.toString)
  }

  def update(folder: Folder) {
    FolderDAO.save(folder)
  }

  /**
   * Delete folder and any reference of it.
   */
  def delete(folderId: UUID, host: String) {

    get(folderId) match {
      case Some(folder) => {
        folder.files.map {
          fileId => {
            files.get(fileId) match {
              case Some(file) => files.removeFile(file.id, host)
              case None =>
            }
          }
        }
        folder.folders.map {
          subfolderId => {
            get(subfolderId)  match {
              case Some(subfolder) => delete(subfolder.id, host)
              case None =>
            }
          }
        }

        if(folder.parentType == "dataset") {
          datasets.removeFolder(folder.parentId, folder.id)
        } else if (folder.parentType.toLowerCase == "folder") {
          removeSubFolder(folder.parentId, folder.id)
        }

        FolderDAO.remove(MongoDBObject("_id" -> new ObjectId(folder.id.stringify)))
      }
      case None =>
    }

  }
  def countByName(name: String,  parentType: String, parentId: String): Long = {
    FolderDAO.count(MongoDBObject("name" -> name, "parentType" -> parentType, "parentId" -> new ObjectId(parentId)))
  }

   def countByDisplayName(displayName: String,  parentType: String, parentId: String): Long = {
     FolderDAO.count(MongoDBObject("displayName" -> displayName, "parentType" -> parentType, "parentId" -> new ObjectId(parentId)))
   }

  /**
   * Add File to Folder
   */
  def addFile(folderId: UUID, fileId: UUID) {
    FolderDAO.update(MongoDBObject("_id" -> new ObjectId(folderId.stringify)), $addToSet("files" -> new ObjectId(fileId.stringify)), false, false, WriteConcern.Safe)
  }

  def removeFile(folderId: UUID, fileId: UUID) {
    FolderDAO.update(MongoDBObject("_id" -> new ObjectId(folderId.stringify)), $pull("files" -> new ObjectId(fileId.stringify)), false, false, WriteConcern.Safe)
  }
  /**
   * Add Subfolder to folder
   */
  def addSubFolder(folderId: UUID, subFolderId: UUID) {
    FolderDAO.update(MongoDBObject("_id" -> new ObjectId(folderId.stringify)), $addToSet("folders" -> new ObjectId(subFolderId.stringify)),false, false, WriteConcern.Safe)
    FolderDAO.update(MongoDBObject("_id" -> new ObjectId(subFolderId.stringify)), $set("parentId" -> new ObjectId(folderId.stringify)), false, false, WriteConcern.Safe)
    FolderDAO.update(MongoDBObject("_id" -> new ObjectId(subFolderId.stringify)), $set("parentType" -> "folder"), false, false, WriteConcern.Safe)
  }

  def removeSubFolder(folderId: UUID, subFolderId: UUID) {
    FolderDAO.update(MongoDBObject("_id" -> new ObjectId(folderId.stringify)), $pull("folders" -> new ObjectId(subFolderId.stringify)), false, false, WriteConcern.Safe)
  }

  def updateParent(folderId: UUID, parent: TypedID) {
    FolderDAO.update(MongoDBObject("_id" -> new ObjectId(folderId.stringify)), $set("parentId" -> new ObjectId(parent.id.stringify)), false, false, WriteConcern.Safe)
    FolderDAO.update(MongoDBObject("_id" -> new ObjectId(folderId.stringify)), $set("parentType" -> parent.objectType), false, false, WriteConcern.Safe)
  }

  def updateName(folderId: UUID, name: String, displayName: String) {
    val result = FolderDAO.update(MongoDBObject("_id" -> new ObjectId(folderId.stringify)),
      $set("name" -> name, "displayName" -> displayName),
      false, false, WriteConcern.Safe)
  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    FolderDAO.update(MongoDBObject("author._id" -> new ObjectId(userId.stringify)),
      $set("author.fullName" -> fullName), false, true, WriteConcern.Safe)
  }

  def findByFileId(file_id:UUID): List[Folder] = {
    FolderDAO.dao.find(MongoDBObject("files" -> new ObjectId(file_id.stringify))).toList
  }

  def findByNameInParent(name: String, parentType: String, parentId: String ): List[Folder] = {
    FolderDAO.find(MongoDBObject("name" -> name, "parentType" -> parentType, "parentId" -> new ObjectId(parentId))).toList
  }

  def findByDisplayNameInParent(name: String, parentType: String, parentId: String ): List[Folder] = {
    FolderDAO.find(MongoDBObject("displayName" -> name, "parentType" -> parentType, "parentId" -> new ObjectId(parentId))).toList
  }

  def findByParentDatasetId(parentId: UUID): List[Folder] = {
    FolderDAO.find(MongoDBObject("parentDatasetId" -> new ObjectId(parentId.stringify))).toList
  }

  def findByParentDatasetIds(parentIds: List[UUID]): List[Folder] = {
    FolderDAO.find("parentDatasetId" $in parentIds.map(x => new ObjectId(x.stringify))).toList
  }
}

object FolderDAO extends ModelCompanion[Folder, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None =>throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Folder, ObjectId](collection = x.collection("folders")){}
  }
}
package controllers

import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Singleton}

import api.Permission
import api.Permission._
import models._
import org.apache.commons.lang.StringEscapeUtils
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import services.{CollectionService, DatasetService, _}
import util.{Formatters, RequiredFieldsConfig}

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

@Singleton
class Vocabularies @Inject()(vocabularies : VocabularyService, datasets: DatasetService, collections: CollectionService, previewsService: PreviewService,
                             spaceService: SpaceService, users: UserService, events: EventService) extends SecuredController {

  def newVocabulary(space: Option[String]) = PermissionAction(Permission.CreateCollection) { implicit request =>
    implicit val user = request.user
    val spacesList = user.get.spaceandrole.map(_.spaceId).flatMap(spaceService.get(_))
    var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
    for (aSpace <- spacesList) {
      //For each space in the list, check if the user has permission to add something to it, if so
      //decode it and add it to the list to pass back to the view.
      if (Permission.checkPermission(Permission.AddResourceToSpace, ResourceRef(ResourceRef.space, aSpace.id))) {
        decodedSpaceList += Utils.decodeSpaceElements(aSpace)
      }
    }
    space match {
      case Some(spaceId) => {
        spaceService.get(UUID(spaceId)) match {
          case Some(s) => Ok(views.html.newVocabulary(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, Some(spaceId)))
          case None => Ok(views.html.newVocabulary(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None))
        }
      }
      case None =>  Ok(views.html.newVocabulary(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None))
    }

  }

  def newCollectionWithParent(parentCollectionId: UUID) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, parentCollectionId))) { implicit request =>
    implicit val user = request.user
    collections.get(parentCollectionId) match {
      case Some(parentCollection) => {

        Ok(views.html.newCollectionWithParent(null, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None, Some(parentCollectionId.toString()), Some(parentCollection.name)))
      }
      case None => Ok(toJson("newCollectionWithParent, no collection matches parentCollectionId"))
    }

  }

  /**
   * Utility method to modify the elements in a collection that are encoded when submitted and stored. These elements
   * are decoded when a view requests the objects, so that they can be human readable.
   *
   * Currently, the following collection elements are encoded:
   *
   * name
   * description
   *
   */
  def decodeCollectionElements(collection: Collection) : Collection = {
      val decodedCollection = collection.copy(name = StringEscapeUtils.unescapeHtml(collection.name),
              							  description = StringEscapeUtils.unescapeHtml(collection.description))

      decodedCollection
  }

  def followingCollections(index: Int, limit: Int, mode: String) = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {

        val title: Option[String] = Some("Following Collections")
        val collectionList = new ListBuffer[Collection]()
        val collectionIds = clowderUser.followedEntities.filter(_.objectType == "collection")
        val collectionIdsToUse = collectionIds.slice(index*limit, (index+1) *limit)
        val prev = index-1
        val next = if(collectionIds.length > (index+1) * limit) {
          index + 1
        } else {
          -1
        }
        for (tidObject <- collectionIdsToUse) {
          val followedCollection = collections.get(tidObject.id)
          followedCollection match {
            case Some(fcoll) => {
              collectionList += fcoll
            }
            case None =>
          }
        }

        val collectionsWithThumbnails = collectionList.map {c =>
          if (c.thumbnail_id.isDefined) {
            c
          } else {
            val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
            c.copy(thumbnail_id = collectionThumbnail)
          }
        }

        //Modifications to decode HTML entities that were stored in an encoded fashion as part
        //of the collection's names or descriptions
        val decodedCollections = ListBuffer.empty[models.Collection]
        for (aCollection <- collectionsWithThumbnails) {
          decodedCollections += Utils.decodeCollectionElements(aCollection)
        }

        //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
        //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
        //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
        val viewMode: Option[String] =
          if (mode == null || mode == "") {
            request.cookies.get("view-mode") match {
              case Some(cookie) => Some(cookie.value)
              case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
            }
          } else {
            Some(mode)
          }

        //Pass the viewMode into the view
        Ok(views.html.users.followingCollections(decodedCollections.toList, prev, next, limit, viewMode, None, title, None))
      }
      case None => InternalServerError("No user defined")
    }

  }

  /**
   * List collections.
   */
  def list(when: String, date: String, limit: Int, space: Option[String], mode: String, owner: Option[String]) = PrivateServerAction { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))
    val collectionSpace = space.flatMap(o => spaceService.get(UUID(o)))
    var title: Option[String] = Some("Collections")

    val collectionList = person match {
      case Some(p) => {
        space match {
          case Some(s) => {
            title = Some(person.get.fullName + "'s Collections in Space <a href="
              + routes.Spaces.getSpace(collectionSpace.get.id) + ">" + collectionSpace.get.name + "</a>")
          }
          case None => {
            title = Some(person.get.fullName + "'s Collections")
          }
        }
        if (date != "") {
          collections.listUser(date, nextPage, limit, request.user, request.user.fold(false)(_.superAdminMode), p)
        } else {
          collections.listUser(limit, request.user, request.user.fold(false)(_.superAdminMode), p)
        }
      }
      case None => {
        space match {
          case Some(s) => {
            title = Some("Collections in Space <a href=" + routes.Spaces.getSpace(collectionSpace.get.id) + ">" + collectionSpace.get.name + "</a>")
            if (date != "") {
              collections.listSpace(date, nextPage, limit, s)
            } else {
              collections.listSpace(limit, s)
            }
          }
          case None => {
            if (date != "") {
              collections.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode))
            } else {
              collections.listAccess(limit, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode))
            }

          }
        }
      }
    }

    // check to see if there is a prev page
    val prev = if (collectionList.nonEmpty && date != "") {
      val first = Formatters.iso8601(collectionList.head.created)
      val c = person match {
        case Some(p) => collections.listUser(first, nextPage=false, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
        case None => {
          space match {
            case Some(s) => collections.listSpace(first, nextPage = false, 1, s)
            case None => collections.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode))
          }
        }
      }
      if (c.nonEmpty && c.head.id != collectionList.head.id) {
        first
      } else {
        ""
      }
    } else {
      ""
    }

    // check to see if there is a next page
    val next = if (collectionList.nonEmpty) {
      val last = Formatters.iso8601(collectionList.last.created)
      val ds = person match {
        case Some(p) => collections.listUser(last, nextPage=true, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
        case None => {
          space match {
            case Some(s) => collections.listSpace(last, nextPage = true, 1, s)
            case None => collections.listAccess(last, nextPage = true, 1, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode))
          }
        }
      }
      if (ds.nonEmpty && ds.head.id != collectionList.last.id) {
        last
      } else {
        ""
      }
    } else {
      ""
    }


    val collectionsWithThumbnails = collectionList.map {c =>
      if (c.thumbnail_id.isDefined) {
        c
      } else {
        val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
        c.copy(thumbnail_id = collectionThumbnail)
      }
    }

    //Modifications to decode HTML entities that were stored in an encoded fashion as part
    //of the collection's names or descriptions
    val decodedCollections = ListBuffer.empty[models.Collection]
    for (aCollection <- collectionsWithThumbnails) {
      decodedCollections += Utils.decodeCollectionElements(aCollection)
    }

    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
    val viewMode: Option[String] =
      if (mode == null || mode == "") {
        request.cookies.get("view-mode") match {
          case Some(cookie) => Some(cookie.value)
          case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
        }
      } else {
        Some(mode)
      }

    //Pass the viewMode into the view
    Ok(views.html.collectionList(decodedCollections.toList, prev, next, limit, viewMode, space, title, owner, when, date))
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description, "created" -> collection.created.toString))
  }

  /**
   * Controller flow to create a new collection. Takes two parameters, name, a String, and description, a String. On success,
   * the browser is redirected to the new collection's page. On error, it is redirected back to the dataset creation
   * page with the appropriate error to be displayed.
   *
   */
  def submit() = PermissionAction(Permission.CreateVocabulary)(parse.multipartFormData) { implicit request =>
      Logger.debug("------- in Collections.submit ---------")
      val colName = request.body.asFormUrlEncoded.getOrElse("name", null)
      val colKeys = request.body.asFormUrlEncoded.getOrElse("keys", null)
      val colDesc = request.body.asFormUrlEncoded.getOrElse("description", null)
      val colSpace = request.body.asFormUrlEncoded.getOrElse("space", List.empty)

      implicit val user = request.user
      user match {
        case Some(identity) => {
          if (colName == null || colKeys == null || colDesc == null || colSpace == null) {
            val spacesList = spaceService.list()
            var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
            for (aSpace <- spacesList) {
              decodedSpaceList += Utils.decodeSpaceElements(aSpace)
            }
            //This case shouldn't happen as it is validated on the client.
            BadRequest(views.html.newVocabulary("Name, Description, or Space was missing during vocabulary creation.", decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None))
          }

          var vocabulary: Vocabulary = null
          if (colSpace.isEmpty || colSpace(0) == "default" || colSpace(0) == "") {
            vocabulary = Vocabulary(name = colName(0).toString, keys = colKeys(0).split(',').toList, description = colDesc(0), created = new Date, author = Some(identity))
          }
          else {
            val stringSpaces = colSpace(0).split(",").toList
            val colSpaces: List[UUID] = stringSpaces.map(aSpace => if (aSpace != "") UUID(aSpace) else None).filter(_ != None).asInstanceOf[List[UUID]]
            vocabulary = Vocabulary(name = colName(0).toString, keys = colKeys(0).split(',').toList, description = colDesc(0), created = new Date, author = Some(identity),spaces = colSpaces)
          }

          Logger.debug("Saving vocabulary " + vocabulary.name)
          vocabularies.insert(vocabulary)
          vocabulary.spaces.map {
            sp => spaceService.get(sp) match {
              case Some(s) => {
                vocabularies.addToSpace(vocabulary.id, s.id)
                //events.addSourceEvent(request.user, collection.id, collection.name, s.id, s.name, "add_collection_space")
              }
              case None => Logger.error(s"space with id $sp on collection $vocabulary.id doesn't exist.")
            }
          }

          //index collection
          val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
          //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "vocabulary", vocabulary.id,
          //List(("name",vocabulary.name), ("description", vocabulary.description), ("created",dateFormat.format(new Date())))}

          //Add to Events Table
          val option_user = users.findByIdentity(identity)
          events.addObjectEvent(option_user, vocabulary.id, vocabulary.name, "create_vocabulary")

          // redirect to collection page
          current.plugin[AdminsNotifierPlugin].foreach {
            _.sendAdminsNotification(Utils.baseUrl(request), "Collection", "added", vocabulary.id.toString, vocabulary.name)
          }
          Ok("not finished yet")
        }
	      case None => Redirect(routes.Collections.list()).flashing("error" -> "You are not authorized to create new collections.")
      }
  }

  /**
   * Vocabulary.
   */
  def vocabulary(id: UUID) = PermissionAction(Permission.ViewVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))){implicit request =>
    implicit val user = request.user

    vocabularies.get(id) match {
      case Some(vocabulary)=> {
        Ok("found vocabulary")
      }
      case None=> BadRequest("No such vocabulary")
    }
  }

  def getUpdatedDatasets(id: UUID, index: Int, limit: Int) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, id))) { implicit request =>
      implicit val user = request.user
    collections.get(id) match {
      case Some(collection) => {

        val datasetsInside = datasets.listCollection(id.stringify)
        val datasetIdsToUse = datasetsInside.slice(index*limit, (index+1)*limit)
        val decodedDatasetsInside = ListBuffer.empty[models.Dataset]
        for (aDataset <- datasetIdsToUse) {
          val dDataset = Utils.decodeDatasetElements(aDataset)
          decodedDatasetsInside += dDataset
        }

        val commentMap = datasetsInside.map { dataset =>
          var allComments = comments.findCommentsByDatasetId(dataset.id)
          dataset.files.map { file =>
            allComments ++= comments.findCommentsByFileId(file)
            sections.findByFileId(file).map { section =>
              allComments ++= comments.findCommentsBySectionId(section.id)
            }
          }
          dataset.id -> allComments.size
        }.toMap
        val prev = index-1
        val next = if(datasetsInside.length > (index+1) * limit) {
          index + 1
        } else {
          -1
        }
        Ok(views.html.collections.datasetsInCollection(decodedDatasetsInside.toList, commentMap, id, prev, next))
      }
      case None => Logger.error("Error getting collection " + id); BadRequest("Collection not found")
    }
  }

  def getUpdatedChildCollections(id: UUID, index: Int, limit: Int) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, id))) { implicit request =>
    implicit val user = request.user
    collections.get(id) match {
      case Some(collection) => {
        val dCollection = Utils.decodeCollectionElements(collection)
        val child_collections_ids = dCollection.child_collection_ids.slice(index*limit, (index+1)*limit)
        val decodedChildCollections = ListBuffer.empty[models.Collection]
        for (child_collection_id <- child_collections_ids) {
          collections.get(child_collection_id) match {
            case Some(child_collection) => {
              val decodedChild = Utils.decodeCollectionElements(child_collection)
              decodedChildCollections += decodedChild
            } case None => {
              Logger.debug("No child collection found for " + child_collection_id)
            }

          }
        }
        val prev = index-1
        val next = if(dCollection.child_collection_ids.length > (index+1) * limit) {
          index + 1
        } else {
          -1
        }

        Ok(views.html.collections.childCollections(decodedChildCollections.toList, collection, prev, next))
      }
      case None => Logger.error("Error getting collection " + id); BadRequest("Collection not found")
    }
  }

  /**
   * Show all users with access to a collection (identified by its id)
   */
  def users(id: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, id))) { implicit request =>
    implicit val user = request.user

    collections.get(id) match {
      case Some(collection) => {
        var userList: List[User] = List.empty
        var userListSpaceRoleTupleMap = Map[UUID, List[Tuple2[String,String]]]() // Map( User-id -> List((Space-name,Role-name)) )

        // Setup userList, add all users of all spaces associated with the collection
        collection.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => userList = spaceService.getUsersInSpace(spaceId) ::: userList
            case None => Redirect (routes.Collections.collection(id)).flashing ("error" -> s"Error: No spaces found for collection $id.");
          }
        }
        userList = userList.distinct.sortBy(_.fullName.toLowerCase)

        // Setup userListSpaceRoleTupleMap
        userList.foreach( usr => userListSpaceRoleTupleMap = userListSpaceRoleTupleMap + (usr.id -> List()) ) // initialize, based upon userList's values
        collection.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => {
              val usersInCurrSpace: List[User] = spaceService.getUsersInSpace(spaceId)
              if (usersInCurrSpace.nonEmpty) {

                usersInCurrSpace.foreach { usr =>
                  spaceService.getRoleForUserInSpace(spaceId, usr.id) match {
                    case Some(role) => userListSpaceRoleTupleMap += ( usr.id -> ((spc.name,role.name) :: userListSpaceRoleTupleMap(usr.id)) )
                    case None => Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: Role not found for collection $id user $usr.")
                  }
                }

              }
            }
            case None => Redirect (routes.Collections.collection(id)).flashing ("error" -> s"Error: No spaces found for collection $id.");
          }
        }
        // Clean-up, and sort space-names per user
        userListSpaceRoleTupleMap = userListSpaceRoleTupleMap filter (_._2.nonEmpty) // remove empty-list Values from Map (and corresponding Key)
        for(k <- userListSpaceRoleTupleMap.keys) userListSpaceRoleTupleMap += ( k -> userListSpaceRoleTupleMap(k).distinct.sortBy(_._1.toLowerCase) )

        if(userList.nonEmpty) {
          val currUserIsAuthor = user.get.id.equals(collection.author.id)
          Ok(views.html.collections.users(collection, userListSpaceRoleTupleMap, currUserIsAuthor, userList))
        }
        else Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: No users found for collection $id.")
      }
      case None => Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: Collection $id not found.")
    }

  }

  def previews(collection_id: UUID) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, collection_id))) { implicit request =>
      collections.get(collection_id) match {
        case Some(collection) => {
          val previewsByCol = previewsService.findByCollectionId(collection_id)
          Ok(views.html.collectionPreviews(collection_id.toString, previewsByCol, Previewers.findCollectionPreviewers))
        }
        case None => {
          Logger.error("Error getting collection " + collection_id);
          BadRequest("Collection not found")
        }
      }
  }

  def listChildCollections(parentCollectionId : String ,when: String, date: String, limit: Int, space: Option[String], mode: String, owner: Option[String]) = PrivateServerAction { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))
    val datasetSpace = space.flatMap(o => spaceService.get(UUID(o)))

    val parentCollection = collections.get(UUID(parentCollectionId))
    var title: Option[String] = Some("Collections")

    val collectionList = person match {
      case Some(p) => {
        parentCollection match {
          case Some(parent) => {
            title = Some(person.get.fullName + "'s Collections in Parent Collection " + parent.name)
          }
          case None => {
            title = Some(person.get.fullName + "'s Collections")
          }
        }
        if (date != "") {
          (collections.listUser(date, nextPage, limit, request.user, request.user.fold(false)(_.superAdminMode), p)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
          //collections.listChildCollections(UUID(parentCollectionId))
        } else {
          (collections.listUser(limit, request.user, request.user.fold(false)(_.superAdminMode), p)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
          //collections.listChildCollections(UUID(parentCollectionId))
        }
      }
      case None => {
        space match {
          case Some(s) => {
            title = Some("Collections in Parent Collection  " + parentCollection.get.name)
            if (date != "") {
              (collections.listSpace(date, nextPage, limit, s)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
              //collections.listChildCollections(UUID(parentCollectionId))
            } else {
              (collections.listSpace(limit, s)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
              //collections.listChildCollections(UUID(parentCollectionId))
            }
          }
          case None => {
            if (date != "") {
              (collections.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode))).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
            } else {
              (collections.listAccess(limit, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode))).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
            }

          }
        }
      }
    }

    //collectionList = collectionList.filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)



    // check to see if there is a prev page
    val prev = if (collectionList.nonEmpty && date != "") {
      val first = Formatters.iso8601(collectionList.head.created)
      val c = person match {
        case Some(p) => (collections.listUser(first, nextPage=false, 1, request.user, request.user.fold(false)(_.superAdminMode), p)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
        case None => {
          space match {
            case Some(s) => (collections.listSpace(first, nextPage = false, 1, s)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
            case None => (collections.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode))).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
          }
        }
      }
      if (c.nonEmpty && c.head.id != collectionList.head.id) {
        first
      } else {
        ""
      }
    } else {
      ""
    }

    // check to see if there is a next page
    val next = if (collectionList.nonEmpty) {
      val last = Formatters.iso8601(collectionList.last.created)
      val ds = person match {
        case Some(p) => (collections.listUser(last, nextPage=true, 1, request.user, request.user.fold(false)(_.superAdminMode), p)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
        case None => {
          space match {
            case Some(s) => (collections.listSpace(last, nextPage = true, 1, s)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
            case None => (collections.listAccess(last, nextPage = true, 1, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode))).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
          }
        }
      }
      if (ds.nonEmpty && ds.head.id != collectionList.last.id) {
        last
      } else {
        ""
      }
    } else {
      ""
    }


    val collectionsWithThumbnails = collectionList.map {c =>
      if (c.thumbnail_id.isDefined) {
        c
      } else {
        val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
        c.copy(thumbnail_id = collectionThumbnail)
      }
    }

    //Modifications to decode HTML entities that were stored in an encoded fashion as part
    //of the collection's names or descriptions
    val decodedCollections = ListBuffer.empty[models.Collection]
    for (aCollection <- collectionsWithThumbnails) {
        decodedCollections += Utils.decodeCollectionElements(aCollection)
    }

    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
    val viewMode: Option[String] =
      if (mode == null || mode == "") {
        request.cookies.get("view-mode") match {
          case Some(cookie) => Some(cookie.value)
          case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
        }
      } else {
        Some(mode)
      }

    //Pass the viewMode into the view
    Ok(views.html.collectionList(decodedCollections.toList, prev, next, limit, viewMode, space, title, owner, when, date))
  }

  private def removeFromSpaceAllowed(collectionId : UUID, spaceId : UUID) : Boolean = {
    return !(collections.hasParentInSpace(collectionId, spaceId))
  }

}


package api

import java.io.FileInputStream
import java.io.BufferedWriter
import java.io.FileWriter

import org.bson.types.ObjectId

import com.mongodb.WriteConcern
import com.mongodb.casbah.Imports._

import models._
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.json.JsValue
import play.api.libs.json.Json._

import javax.inject.Inject

import scala.collection.mutable.ListBuffer

import org.json.JSONObject

import Transformation.LidoToCidocConvertion

import jsonutils.JsonUtil

import services._
import fileutils.FilesUtils

import controllers.Previewers

import play.api.libs.json.JsString
import scala.Some
import services.DumpOfFile
import services.ExtractorMessage
import play.api.mvc.ResponseHeader
import scala.util.parsing.json.JSONArray
import models.Preview
import play.api.mvc.SimpleResult
import models.File
import play.api.libs.json.JsObject
import play.api.Play.configuration
import com.wordnik.swagger.annotations.{ApiOperation, Api}

import securesocial.core.Identity

/**
 * Json API for files.
 *
 * @author Luigi Marini
 *
 */
@Api(value = "/files", listingPath = "/api-docs.json/files", description = "A file is the raw bytes plus metadata.")
class Files @Inject()(
  files: FileService,
  datasets: DatasetService,
  collections: CollectionService,
  queries: MultimediaQueryService,
  tags: TagService,
  comments: CommentService,
  extractions: ExtractionService,
  previews: PreviewService,
  threeD: ThreeDService,
  sqarql: RdfSPARQLService,
  accessRights: UserAccessRightsService,
  thumbnails: ThumbnailService,
  appConfiguration: AppConfigurationService) extends ApiController {

  @ApiOperation(value = "Retrieve physical file object metadata",
      notes = "Get metadata of the file object (not the resource it describes) as JSON. For example, size of file, date created, content type, filename.",
      responseClass = "None", httpMethod = "GET")
  def get(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile), resourceId = Some(id)) {
    implicit request =>
      Logger.info("GET file with id " + id)
      files.get(id) match {
        case Some(file) => Ok(jsonFile(file))
        case None => {
          Logger.error("Error getting file" + id);
          InternalServerError
        }
      }
  }
  /**
   * List all files.
   */
  @ApiOperation(value = "List all files", notes = "Returns list of files and descriptions.", responseClass = "None", httpMethod = "GET")
  def list = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ListFiles)) {
    request =>
      var list: List[JsValue] = List.empty
      request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	list = for (f <- files.listFiles() if(checkAccessForFileUsingRightsList(f, request.user , "view", rightsForUser))) yield jsonFile(f)
	        }
	        case None=>{
	          list = for (f <- files.listFiles() if(checkAccessForFile(f, request.user , "view"))) yield jsonFile(f)
	        }
	      }

      Ok(toJson(list))
  }

  def downloadByDatasetAndFilename(datasetId: UUID, filename: String, preview_id: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.DownloadFiles), resourceId = datasets.getFileId(datasetId, filename)) {
      request =>
        datasets.getFileId(datasetId, filename) match {
          case Some(id) => Redirect(routes.Files.download(id))
          case None => Logger.error("Error getting dataset" + datasetId); InternalServerError
        }
    }

  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  @ApiOperation(value = "Download file",
      notes = "Can use Chunked transfer encoding if the HTTP header RANGE is set.",
      responseClass = "None", httpMethod = "GET")
  def download(id: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.DownloadFiles), resourceId = Some(id)) {
      request =>

        files.getBytes(id) match {
          case Some((inputStream, filename, contentType, contentLength)) => {

            request.headers.get(RANGE) match {
              case Some(value) => {
                val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                  case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                  case x => (x(0).toLong, x(1).toLong)
                }

                range match {
                  case (start, end) =>
                    inputStream.skip(start)
                    SimpleResult(
                      header = ResponseHeader(PARTIAL_CONTENT,
                        Map(
                          CONNECTION -> "keep-alive",
                          ACCEPT_RANGES -> "bytes",
                          CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                          CONTENT_LENGTH -> (end - start + 1).toString,
                          CONTENT_TYPE -> contentType
                        )
                      ),
                      body = Enumerator.fromStream(inputStream)
                    )
                }
              }
              case None => {
                Ok.chunked(Enumerator.fromStream(inputStream))
                  .withHeaders(CONTENT_TYPE -> contentType)
                  .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
              }
            }
          }
          case None => {
            Logger.error("Error getting file" + id)
            NotFound
          }
        }
    }

  /**
   *
   * Download query used by Versus
   *
   */
  def downloadquery(id: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.DownloadFiles), resourceId = Some(id)) {
      request =>
        queries.get(id) match {
          case Some((inputStream, filename, contentType, contentLength)) => {
            request.headers.get(RANGE) match {
              case Some(value) => {
                val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                  case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                  case x => (x(0).toLong, x(1).toLong)
                }
                range match {
                  case (start, end) =>
                    inputStream.skip(start)
                    SimpleResult(
                      header = ResponseHeader(PARTIAL_CONTENT,
                        Map(
                          CONNECTION -> "keep-alive",
                          ACCEPT_RANGES -> "bytes",
                          CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                          CONTENT_LENGTH -> (end - start + 1).toString,
                          CONTENT_TYPE -> contentType
                        )
                      ),
                      body = Enumerator.fromStream(inputStream)
                    )
                }
              }
              case None => {
                Ok.chunked(Enumerator.fromStream(inputStream))
                  .withHeaders(CONTENT_TYPE -> contentType)
                  .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
              }
            }
          }
          case None => {
            Logger.error("Error getting file" + id)
            NotFound
          }
        }
    }

  /**
   * Add metadata to file.
   */
  @ApiOperation(value = "Add technical metadata to file",
      notes = "Metadata in attached JSON object will describe the file's described resource, not the file object itself.",
      responseClass = "None", httpMethod = "POST")
  def addMetadata(id: UUID) =
    SecuredAction(authorization = WithPermission(Permission.AddFilesMetadata), resourceId = Some(id)) {
      request =>
        Logger.debug(s"Adding metadata to file $id")
        val doc = com.mongodb.util.JSON.parse(Json.stringify(request.body)).asInstanceOf[DBObject]
        files.get(id) match {
          case Some(x) => {
            files.addMetadata(id, request.body)
            index(id)
          }
          case None => Logger.error(s"Error getting file $id"); NotFound
        }

        Logger.debug(s"Updating previews.files $id with $doc")
        Ok(toJson("success"))
    }


  /**
   * Upload file using multipart form enconding.
   */
  @ApiOperation(value = "Upload file",
      notes = "Upload the attached file using multipart form enconding. Returns file id as JSON object. ID can be used to work on the file using the API. Uploaded file can be an XML metadata file.",
      responseClass = "None", httpMethod = "POST")
  def upload(showPreviews: String = "DatasetLevel", originalZipFile: String = "", fileIsPublic: String = "false") = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) {
    implicit request =>
      request.user match {
        case Some(user) => {
	      request.body.file("File").map { f =>        
	          var nameOfFile = f.filename
	          var flags = ""
	          if(nameOfFile.toLowerCase().endsWith(".ptm")){
		          	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
		              if(thirdSeparatorIndex >= 0){
		                var firstSeparatorIndex = nameOfFile.indexOf("_")
		                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
		            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
		            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
		              }
	          }
	        
	        Logger.debug("Uploading file " + nameOfFile)
	        // store file
	        var realUser = user
	          if(!originalZipFile.equals("")){
	             files.get(new UUID(originalZipFile)) match{
	               case Some(originalFile) => {
	                 realUser = originalFile.author
	               }
	               case None => {}
	             }
	         }
	        

	        val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, realUser, showPreviews, realUser.fullName.equals("Anonymous User")||fileIsPublic.toLowerCase.equals("true")) 
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
	            	            
	            val id = f.id	            
	            accessRights.addPermissionLevel(realUser, id.stringify, "file", "administrate")	            
	            if(showPreviews.equals("FileLevel"))
	            	flags = flags + "+filelevelshowpreviews"
	            else if(showPreviews.equals("None"))
	            	flags = flags + "+nopreviews"
	            var fileType = f.contentType
	            if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
	            	fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
	            	if(fileType.startsWith("ERROR: ")){
	            		Logger.error(fileType.substring(7))
	            		InternalServerError(fileType.substring(7))
	            	}
	            	if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") ){
					        	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
					              if(thirdSeparatorIndex >= 0){
					                var firstSeparatorIndex = nameOfFile.indexOf("_")
					                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
					            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
					            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
					            	files.renameFile(f.id, nameOfFile)
					              }
					        	  files.setContentType(f.id, fileType)
					          }
	            }
	            else if(nameOfFile.toLowerCase().endsWith(".mov")){
							  fileType = "ambiguous/mov";
						  }
	            
	            current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}

                  val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
                  // TODO RK : need figure out if we can use https
                  val host = "http://" + request.host + request.path.replaceAll("api/files$", "")

	            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))}
	            	            
	            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(id, xmlToJSON)
	              
	              Logger.debug("xmlmd=" + xmlToJSON)
	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("xmlmetadata", xmlToJSON)))
		            }
	              
	              //add file to RDF triple store if triple store is used
	             configuration.getString("userdfSPARQLStore").getOrElse("no") match {
                      case "yes" => sqarql.addFileToGraph(f.id)
                      case _ => {}
                    }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType)))
		            }
	            }
	            
	            Ok(toJson(Map("id"->id.stringify)))
	            current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("File","added",id.stringify, nameOfFile)}
	            Ok(toJson(Map("id"->id.stringify)))
	          }
	          case None => {
	            Logger.error("Could not retrieve file that was just saved.")
	            InternalServerError("Error uploading file")
	          }
	        }
	      }.getOrElse {
	         BadRequest(toJson("File not attached."))
	      }
        }

        case None => BadRequest(toJson("Not authorized."))
      }
  }

  /**
   * Send job for file preview(s) generation at a later time.
   */
  @ApiOperation(value = "(Re)send preprocessing job for file",
      notes = "Force Medici to (re)send preprocessing job for selected file, processing the file as a file of the selected MIME type. Returns file id on success. In the requested file type, replace / with __ (two underscores).",
      responseClass = "None", httpMethod = "POST")
  def sendJob(file_id: UUID, fileType: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.CreateFiles), Some(file_id)) {
    implicit request =>
      files.get(file_id) match {
        case Some(theFile) => {
          var nameOfFile = theFile.filename
          var flags = ""
          if (nameOfFile.toLowerCase().endsWith(".ptm")) {
            var thirdSeparatorIndex = nameOfFile.indexOf("__")
            if (thirdSeparatorIndex >= 0) {
              var firstSeparatorIndex = nameOfFile.indexOf("_")
              var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
              flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
              nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
            }
          }

          val showPreviews = theFile.showPreviews

          Logger.debug("(Re)sending job for file " + nameOfFile)

          val id = theFile.id
          if (showPreviews.equals("None"))
            flags = flags + "+nopreviews"

          val key = "unknown." + "file." + fileType.replace("__", ".")
          // TODO RK : need figure out if we can use https
          val host = "http://" + request.host + request.path.replaceAll("api/files/sendJob/[A-Za-z0-9_]*/.*$", "")

          // TODO replace null with None
          current.plugin[RabbitmqPlugin].foreach {
            _.extract(ExtractorMessage(id, id, host, key, Map.empty, theFile.length.toString, null, flags))
          }

          Ok(toJson(Map("id" -> id.stringify)))

        }
        case None => {
          BadRequest(toJson("File not found."))
        }
      }
  }


  /**
   * Upload a file to a specific dataset
   */
  @ApiOperation(value = "Upload a file to a specific dataset",
      notes = "Uploads the file, then links it with the dataset. Returns file id as JSON object. ID can be used to work on the file using the API. Uploaded file can be an XML metadata file to be added to the dataset.",
      responseClass = "None", httpMethod = "POST")
  def uploadToDataset(dataset_id: UUID, showPreviews: String="DatasetLevel", originalZipFile: String = "", fileIsPublic: String = "false") = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateDatasets), Some(dataset_id)) { implicit request =>
    request.user match {
     case Some(user) => {
      datasets.get(dataset_id) match {
       case Some(dataset) => {
        request.body.file("File").map { f =>
          		var nameOfFile = f.filename
	            var flags = ""
	            if(nameOfFile.toLowerCase().endsWith(".ptm")){
	            	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
		              if(thirdSeparatorIndex >= 0){
		                var firstSeparatorIndex = nameOfFile.indexOf("_")
		                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
		            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
		            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
		              }
	            }
          
          Logger.debug("Uploading file " + nameOfFile)         
          // store file
          var realUser = user
          if(!originalZipFile.equals("")){
             files.get(new UUID(originalZipFile)) match{
               case Some(originalFile) => {
                 realUser = originalFile.author
               }
               case None => {}
             }
         }          
          val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, realUser, showPreviews, realUser.fullName.equals("Anonymous User")||fileIsPublic.toLowerCase.equals("true"))
          val uploadedFile = f         
          
          // submit file for extraction
          file match {
            case Some(f) => {
                            
              val id = f.id.toString
              accessRights.addPermissionLevel(realUser, id, "file", "administrate")
              if(showPreviews.equals("FileLevel"))
	            flags = flags + "+filelevelshowpreviews"
	          else if(showPreviews.equals("None"))
	            flags = flags + "+nopreviews"
	          var fileType = f.contentType
	          if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.endsWith(".zip")){
	        	  fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "dataset")			          

	        	  if(fileType.startsWith("ERROR: ")){
	        		  Logger.error(fileType.substring(7))
	        		  InternalServerError(fileType.substring(7))
				  }
	        	  if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") ){
					        	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
					              if(thirdSeparatorIndex >= 0){
					                var firstSeparatorIndex = nameOfFile.indexOf("_")
					                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
					            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
					            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
					            	files.renameFile(f.id, nameOfFile)
					              }
					        	  files.setContentType(f.id, fileType)
					          }
	          }
	          else if(nameOfFile.toLowerCase().endsWith(".mov")){
							  fileType = "ambiguous/mov";
						  }
	              
              current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
              
	          // TODO RK need to replace unknown with the server name
	          val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
	          // TODO RK : need figure out if we can use https
	          val host = "http://" + request.host + request.path.replaceAll("api/uploadToDataset/[A-Za-z0-9_]*$", "")
	              
	          current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(new UUID(id), new UUID(id), host, key, Map.empty, f.length.toString, dataset_id, flags)) }
	          
	          //for metadata files
              if(fileType.equals("application/xml") || fileType.equals("text/xml")){
            	  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
            	  		  files.addXMLMetadata(new UUID(id), xmlToJSON)

            			  Logger.debug("xmlmd=" + xmlToJSON)

            			  current.plugin[ElasticsearchPlugin].foreach{
            		  		_.index("data", "file", new UUID(id), List(("filename",f.filename), ("contentType", f.contentType),("datasetId",dataset.id.toString),("datasetName",dataset.name), ("xmlmetadata", xmlToJSON)))
            	  		  }
              }
              else{
            	  current.plugin[ElasticsearchPlugin].foreach{
            		  _.index("data", "file", new UUID(id), List(("filename",nameOfFile), ("contentType", f.contentType),("datasetId",dataset.id.toString),("datasetName",dataset.name)))
            	  }
              }
                           
              // add file to dataset   
              // TODO create a service instead of calling salat directly
              val theFile = files.get(f.id)
              if(theFile.isEmpty){
                 Logger.error("Could not retrieve file that was just saved.")
                 InternalServerError("Error uploading file")                
              }
              else{
            	  datasets.addFile(dataset.id, theFile.get)
	              if(!theFile.get.xmlMetadata.isEmpty){
	            	  datasets.index(dataset_id)
			      	}	

            	  // TODO RK need to replace unknown with the server name and dataset type
            	  val dtkey = "unknown." + "dataset." + "unknown"

              	  current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(dataset_id, dataset_id, host, dtkey, Map.empty, f.length.toString, dataset_id, "")) }

            	  Logger.info("Uploading Completed")
              
	              //add file to RDF triple store if triple store is used
	              if(fileType.equals("application/xml") || fileType.equals("text/xml")){
			             configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
				             case "yes" => {
				               sqarql.addFileToGraph(f.id)
				               sqarql.linkFileToDataset(f.id,dataset_id)
				             }
				             case _ => {}
			             }
	              }

              //sending success message
              Ok(toJson(Map("id" -> id)))
              current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("File","added",id, nameOfFile)}
              Ok(toJson(Map("id" -> id)))
             }
            }
            case None => {
              Logger.error("Could not retrieve file that was just saved.")
              InternalServerError("Error uploading file")
            }
          }

        }.getOrElse {
          BadRequest(toJson("File not attached."))
        }
      } 
        case None => { Logger.error("Error getting dataset" + dataset_id); InternalServerError }
      }
     }
        
        case None => BadRequest(toJson("Not authorized."))
    }
  }
  

  /**
   * Upload intermediate file of extraction chain using multipart form enconding and continue chaining.
   */
  def uploadIntermediate(originalIdAndFlags: String) = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) {
    implicit request =>
      request.user match {
        case Some(user) => {
          request.body.file("File").map {
            f =>
              var originalId = originalIdAndFlags
              var flags = ""
              if (originalIdAndFlags.indexOf("+") != -1) {
                originalId = originalIdAndFlags.substring(0, originalIdAndFlags.indexOf("+"));
                flags = originalIdAndFlags.substring(originalIdAndFlags.indexOf("+"));
              }

              Logger.debug("Uploading intermediate file " + f.filename + " associated with original file with id " + originalId)
              // store file
              val file = files.save(new FileInputStream(f.ref.file), f.filename, f.contentType, user)
              val uploadedFile = f
              file match {
                case Some(f) => {
                  files.setIntermediate(f.id)
                  var fileType = f.contentType
                  if (fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.toLowerCase().endsWith(".zip")) {
                    fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "file")
                    if (fileType.startsWith("ERROR: ")) {
                      Logger.error(fileType.substring(7))
                      InternalServerError(fileType.substring(7))
                    }
                  }
                  else if (f.filename.toLowerCase().endsWith(".mov")) {
                    fileType = "ambiguous/mov";
                  }

                  val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
                  // TODO RK : need figure out if we can use https
                  val host = "http://" + request.host + request.path.replaceAll("api/files/uploadIntermediate/[A-Za-z0-9_+]*$", "")
                  val id = f.id
                  // TODO replace null with None
                  current.plugin[RabbitmqPlugin].foreach {
                    _.extract(ExtractorMessage(UUID(originalId), id, host, key, Map.empty, f.length.toString, null, flags))
                  }
                  current.plugin[ElasticsearchPlugin].foreach {
                    _.index("files", "file", id, List(("filename", f.filename), ("contentType", f.contentType)))
                  }
                  Ok(toJson(Map("id" -> id.stringify)))
                }
                case None => {
                  Logger.error("Could not retrieve file that was just saved.")
                  InternalServerError("Error uploading file")
                }
              }
          }.getOrElse {
            BadRequest(toJson("File not attached."))
          }
        }

        case None => BadRequest(toJson("Not authorized."))
      }
  }

  /**
   * Upload metadata for preview and attach it to a file.
   */
  def uploadPreview(file_id: UUID) = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles), Some(file_id)) {
    implicit request =>
      request.body.file("File").map {
        f =>
          Logger.debug("Uploading file " + f.filename)
          // store file
          val id = previews.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
          Ok(toJson(Map("id" -> id)))
      }.getOrElse {
        BadRequest(toJson("File not attached."))
      }
  }

  /**
   * Add preview to file.
   */
  @ApiOperation(value = "Attach existing preview to file",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def attachPreview(file_id: UUID, preview_id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateFiles), resourceId = Some(file_id)) {
    request =>
    // Use the "extractor_id" field contained in the POST data.  Use "Other" if absent.
      val eid = (request.body \ "extractor_id").asOpt[String]
      val extractor_id = if (eid.isDefined) {
        Some(UUID(eid.get))
      } else {
        Logger.info("api.Files.attachPreview(): No \"extractor_id\" specified in request, set it to None.  request.body: " + request.body.toString)
        None
      }
      request.body match {
        case JsObject(fields) => {
          files.get(file_id) match {
            case Some(file) => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
                  // TODO replace null with None
                  previews.attachToFile(preview_id, file_id, extractor_id, request.body)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
            //If file to be previewed is not found, just delete the preview
            case None => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  Logger.debug("File not found. Deleting previews.files " + preview_id)
                  previews.removePreview(preview)
                  BadRequest(toJson("File not found. Preview deleted."))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }

  @ApiOperation(value = "Get the user-generated metadata of the selected file in an RDF file",
	      notes = "",
	      responseClass = "None", httpMethod = "GET")
  def getRDFUserMetadata(id: UUID, mappingNumber: String="1") = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata), resourceId = Some(id)) {implicit request =>  
   current.plugin[RDFExportService].isDefined match{
    case true => {
      current.plugin[RDFExportService].get.getRDFUserMetadataFile(id.stringify, mappingNumber) match{
        case Some(resultFile) =>{
          Ok.chunked(Enumerator.fromStream(new FileInputStream(resultFile)))
			            	.withHeaders(CONTENT_TYPE -> "application/rdf+xml")
			            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + resultFile.getName()))
        }
        case None => BadRequest(toJson("File not found " + id))
      }
    }
    case false=>{
      Ok("RDF export plugin not enabled")
    }      
   }
  }

  def jsonToXML(theJSON: String): java.io.File = {

    val jsonObject = new JSONObject(theJSON)
    var xml = org.json.XML.toString(jsonObject)

    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while (currStart != -1) {
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1, currStart)
      currEnd = xml.indexOf(">", currStart + 1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart, currEnd + 1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd + 1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1)

    val xmlFile = java.io.File.createTempFile("xml", ".xml")
    val fileWriter = new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()
    
    return xmlFile
  }
  
  @ApiOperation(value = "Get URLs of file's RDF metadata exports.",
	      notes = "URLs of metadata files exported from XML (if the file was an XML metadata file) as well as the URL used to export the file's user-generated metadata as RDF.",
	      responseClass = "None", httpMethod = "GET")
  def getRDFURLsForFile(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata), resourceId = Some(id)) { request =>
    current.plugin[RDFExportService].isDefined match{
      case true =>{
	    current.plugin[RDFExportService].get.getRDFURLsForFile(id.stringify)  match {
	      case Some(listJson) => {
	        Ok(listJson) 
	      }
	      case None => {Logger.error("Error getting file" + id); InternalServerError}
	    }
      }
      case false => {
        Ok("RDF export plugin not enabled")
      }
    }
  }
  
    @ApiOperation(value = "Add user-generated metadata to file",
	      notes = "Metadata in attached JSON object will describe the file's described resource, not the file object itself.",
	      responseClass = "None", httpMethod = "POST")
    def addUserMetadata(id: UUID) = SecuredAction(authorization = WithPermission(Permission.AddFilesMetadata), resourceId = Some(id)) {
        implicit request =>
          Logger.debug("Adding user metadata to file " + id)
          val theJSON = Json.stringify(request.body)
          files.addUserMetadata(id, theJSON)
          files.index(id)
          configuration.getString("userdfSPARQLStore").getOrElse("no") match {
            case "yes" => {
              files.setUserMetadataWasModified(id, true)
            }
            case _ => {}
          }

          Ok(toJson(Map("status" -> "success")))
      }

  def jsonFile(file: File): JsValue = {
    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "content-type" -> file.contentType, "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString,
    		"authorId" -> file.author.identityId.userId))
  }

  def jsonFileWithThumbnail(file: File, user: Option[Identity] = None, rightsForUser: Option[UserPermissions] = None): JsValue = {
    var userRequested = "None"
    var userCanEdit = false
    var fileThumbnail = "None"
    if (!file.thumbnail_id.isEmpty)
      fileThumbnail = file.thumbnail_id.toString().substring(5, file.thumbnail_id.toString().length - 1)
      
    user match{
        case Some(theUser)=>{
          userRequested = theUser.fullName
          userCanEdit = checkAccessForFileUsingRightsList(file, user, "modify", rightsForUser)
        }
        case None=>{
          userRequested = "None"
          userCanEdit = checkAccessForFile(file, user, "modify")
        }
      }

    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType, "dateCreated" -> file.uploadDate.toString(), "thumbnail" -> fileThumbnail,
    		"authorId" -> file.author.identityId.userId, "usercanedit" -> userCanEdit.toString, "userThatRequested" -> userRequested   ))
  }

  def toDBObject(fields: Seq[(String, JsValue)]): DBObject = {
    fields.map(field =>
      field match {
        // TODO handle jsarray
        //          case (key, JsArray(value: Seq[JsValue])) => MongoDBObject(key -> getValueForSeq(value))
        case (key, jsObject: JsObject) => MongoDBObject(key -> toDBObject(jsObject.fields))
        case (key, jsValue: JsValue) => MongoDBObject(key -> jsValue.as[String])
      }
    ).reduce((left: DBObject, right: DBObject) => left ++ right)
  }

  @ApiOperation(value = "List file previews",
      notes = "Return the currently existing previews' basic characteristics (id, filename, content type) of the selected file.",
      responseClass = "None", httpMethod = "GET")
  def filePreviewsList(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile), resourceId = Some(id)) {
    request =>
      files.get(id) match {
        case Some(file) => {
          val filePreviews = previews.findByFileId(file.id);
          val list = for (prv <- filePreviews) yield jsonPreview(prv)
          Ok(toJson(list))
        }
        case None => {
          Logger.error("Error getting file" + id);
          InternalServerError
        }
      }
  }

  def jsonPreview(preview: Preview): JsValue = {
    toJson(Map("id" -> preview.id.toString, "filename" -> getFilenameOrEmpty(preview), "contentType" -> preview.contentType))
  }

  def getFilenameOrEmpty(preview: Preview): String = {
    preview.filename match {
      case Some(strng) => strng
      case None => ""
    }
  }

  /**
   * Add 3D geometry file to file.
   */
  def attachGeometry(file_id: UUID, geometry_id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateFiles), resourceId = Some(file_id)) {
    request =>
      request.body match {
        case JsObject(fields) => {
          files.get(file_id) match {
            case Some(file) => {
              threeD.getGeometry(geometry_id) match {
                case Some(geometry) =>
                  threeD.updateGeometry(file_id, geometry_id, fields)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Geometry file not found"))
              }
            }
            case None => BadRequest(toJson("File not found " + file_id))
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }


  /**
   * Add 3D texture to file.
   */
  def attachTexture(file_id: UUID, texture_id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateFiles), resourceId = Some(file_id)) {
    request =>
      request.body match {
        case JsObject(fields) => {
          files.get((file_id)) match {
            case Some(file) => {
              threeD.getTexture(texture_id) match {
                case Some(texture) => {
                  threeD.updateTexture(file_id, texture_id, fields)
                  Ok(toJson(Map("status" -> "success")))
                }
                case None => BadRequest(toJson("Texture file not found"))
              }
            }
            case None => BadRequest(toJson("File not found " + file_id))
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }

  /**
   * Add thumbnail to file.
   */
  @ApiOperation(value = "Add thumbnail to file", notes = "Attaches an already-existing thumbnail to a file.", responseClass = "None", httpMethod = "POST")
  def attachThumbnail(file_id: UUID, thumbnail_id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.CreateFiles), resourceId = Some(file_id)) {
    implicit request =>
      files.get(file_id) match {
        case Some(file) => {
          thumbnails.get(thumbnail_id) match {
            case Some(thumbnail) => {
              files.updateThumbnail(file_id, thumbnail_id)
              val datasetList = datasets.findByFileId(file.id)
              for (dataset <- datasetList) {
                if (dataset.thumbnail_id.isEmpty) {
                  datasets.updateThumbnail(dataset.id, thumbnail_id)
                  val collectionList = collections.listInsideDataset(dataset.id)                  
                  for(collection <- collectionList){
                    if (collection.thumbnail_id.isEmpty) {
                      collections.updateThumbnail(collection.id, thumbnail_id)
                    }
                  }                  
                }
              }

              Ok(toJson(Map("status" -> "success")))
            }
            case None => BadRequest(toJson("Thumbnail not found"))
          }
        }
        case None => BadRequest(toJson("File not found " + file_id))
      }
  }

  /**
   * Find geometry file for given 3D file and geometry filename.
   */
  def getGeometry(three_d_file_id: UUID, filename: String) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile), resourceId = Some(three_d_file_id)) {
      request =>
        threeD.findGeometry(three_d_file_id, filename) match {
          case Some(geometry) => {

            threeD.getGeometryBlob(geometry.id) match {

              case Some((inputStream, filename, contentType, contentLength)) => {
                request.headers.get(RANGE) match {
                  case Some(value) => {
                    val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                      case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                      case x => (x(0).toLong, x(1).toLong)
                    }
                    range match {
                      case (start, end) =>

                        inputStream.skip(start)
                        import play.api.mvc.{SimpleResult, ResponseHeader}
                        SimpleResult(
                          header = ResponseHeader(PARTIAL_CONTENT,
                            Map(
                              CONNECTION -> "keep-alive",
                              ACCEPT_RANGES -> "bytes",
                              CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                              CONTENT_LENGTH -> (end - start + 1).toString,
                              CONTENT_TYPE -> contentType
                            )
                          ),
                          body = Enumerator.fromStream(inputStream)
                        )
                    }
                  }
                  case None => {
                    Ok.chunked(Enumerator.fromStream(inputStream))
                      .withHeaders(CONTENT_TYPE -> contentType)
                      .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))

                  }
                }
              }
              case None => Logger.error("No geometry file found: " + geometry.id); InternalServerError("No geometry file found")

            }

          }
          case None => Logger.error("Geometry file not found"); InternalServerError
        }
    }

  /**
   * Find texture file for given 3D file and texture filename.
   */
  def getTexture(three_d_file_id: UUID, filename: String) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile), resourceId = Some(three_d_file_id)) {
      request =>
        threeD.findTexture(three_d_file_id, filename) match {
          case Some(texture) => {

            threeD.getBlob(texture.id) match {

              case Some((inputStream, filename, contentType, contentLength)) => {
                request.headers.get(RANGE) match {
                  case Some(value) => {
                    val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                      case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                      case x => (x(0).toLong, x(1).toLong)
                    }
                    range match {
                      case (start, end) =>

                        inputStream.skip(start)

                        SimpleResult(
                          header = ResponseHeader(PARTIAL_CONTENT,
                            Map(
                              CONNECTION -> "keep-alive",
                              ACCEPT_RANGES -> "bytes",
                              CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                              CONTENT_LENGTH -> (end - start + 1).toString,
                              CONTENT_TYPE -> contentType
                            )
                          ),
                          body = Enumerator.fromStream(inputStream)
                        )
                    }
                  }
                  case None => {
                    Ok.stream(Enumerator.fromStream(inputStream))
                      .withHeaders(CONTENT_TYPE -> contentType)
                      //.withHeaders(CONTENT_LENGTH -> contentLength.toString)
                      .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))

                  }
                }
              }
              case None => Logger.error("No texture file found: " + texture.id.toString()); InternalServerError("No texture found")

            }

          }
          case None => Logger.error("Texture file not found"); InternalServerError
        }
    }

  // ---------- Tags related code starts ------------------
  /**
   * REST endpoint: GET: gets the tag data associated with this file.
   */
  @ApiOperation(value = "Gets tags of a file", notes = "Returns a list of strings, List[String].", responseClass = "None", httpMethod = "GET")
  def getTags(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
    implicit request =>
      Logger.info("Getting tags for file with id " + id)
      /* Found in testing: given an invalid ObjectId, a runtime exception
       * ("IllegalArgumentException: invalid ObjectId") occurs in Services.files.get().
       * So check it first.
       */
      if (UUID.isValid(id.stringify)) {
        files.get(id) match {
          case Some(file) => Ok(Json.obj("id" -> file.id.toString, "filename" -> file.filename,
            "tags" -> Json.toJson(file.tags.map(_.name))))
          case None => {
            Logger.error("The file with id " + id + " is not found.")
            NotFound(toJson("The file with id " + id + " is not found."))
          }
        }
      } else {
        Logger.error("The given id " + id + " is not a valid ObjectId.")
        BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
      }
  }

  /*
   *  Helper function to handle adding and removing tags for files/datasets/sections.
   *  Input parameters:
   *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
   *      op_type:  one of the two strings "add", "remove"
   *      id:       the id in the original addTags call
   *      request:  the request in the original addTags call
   *  Return type:
   *      play.api.mvc.SimpleResult[JsValue]
   *      in the form of Ok, NotFound and BadRequest
   *      where: Ok contains the JsObject: "status" -> "success", the other two contain a JsString,
   *      which contains the cause of the error, such as "No 'tags' specified", and
   *      "The file with id 5272d0d7e4b0c4c9a43e81c8 is not found".
   */
  def addTagsHelper(obj_type: TagCheckObjType, id: UUID, request: RequestWithUser[JsValue]): SimpleResult = {

    val (not_found, error_str) = tags.addTagsHelper(obj_type, id, request)

    // Now the real work: adding the tags.
    if ("" == error_str) {
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  def removeTagsHelper(obj_type: TagCheckObjType, id: UUID, request: RequestWithUser[JsValue]): SimpleResult = {

    val (not_found, error_str) = tags.removeTagsHelper(obj_type, id, request)

    if ("" == error_str) {
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  /**
   * REST endpoint: POST: Adds tags to a file.
   * Tag's (name, userId, extractor_id) tuple is used as a unique key.
   * In other words, the same tag names but diff userId or extractor_id are considered as diff tags,
   * so will be added.
   */
  @ApiOperation(value = "Adds tags to a file",
      notes = "Tag's (name, userId, extractor_id) tuple is used as a unique key. In other words, the same tag names but diff userId or extractor_id are considered as diff tags, so will be added.  The tags are expected as a list of strings: List[String].  An example is:<br>    curl -H 'Content-Type: application/json' -d '{\"tags\":[\"namo\", \"amitabha\"], \"extractor_id\": \"curl\"}' \"http://localhost:9000/api/files/533c2389e4b02a14f0943356/tags?key=theKey\"",
      responseClass = "None", httpMethod = "POST")
  def addTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateTagsFiles)) {
    implicit request =>
      addTagsHelper(TagCheck_File, id, request)
  }

  /**
   * REST endpoint: POST: removes tags.
   * Tag's (name, userId, extractor_id) tuple is used as a unique key.
   * In other words, the same tag names but diff userId or extractor_id are considered as diff tags.
   * Current implementation enforces the restriction which only allows the tags to be removed by
   * the same user or extractor.
   */
  @ApiOperation(value = "Removes tags of a file",
      notes = "Tag's (name, userId, extractor_id) tuple is unique key. Same tag names but diff userId or extractor_id are considered diff tags. Tags can only be removed by the same user or extractor.  The tags are expected as a list of strings: List[String].",
      responseClass = "None", httpMethod = "POST")
  def removeTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.DeleteTagsFiles)) {
    implicit request =>
      removeTagsHelper(TagCheck_File, id, request)
  }

  /**
   * REST endpoint: POST: removes all tags of a file.
   * This is a big hammer -- it does not check the userId or extractor_id and
   * forcefully remove all tags for this id.  It is mainly intended for testing.
   */
  @ApiOperation(value = "Removes all tags of a file",
      notes = "This is a big hammer -- it does not check the userId or extractor_id and forcefully remove all tags for this file.  It is mainly intended for testing.",
      responseClass = "None", httpMethod = "POST")
  def removeAllTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.DeleteTagsFiles)) {
    implicit request =>
      Logger.info("Removing all tags for file with id: " + id)
      if (UUID.isValid(id.stringify)) {
        files.get(id) match {
          case Some(file) => {
            files.removeAllTags(id)
            Ok(Json.obj("status" -> "success"))
          }
          case None => {
            Logger.error("The file with id " + id + " is not found.")
            NotFound(toJson("The file with id " + id + " is not found."))
          }
        }
      } else {
        Logger.error("The given id " + id + " is not a valid ObjectId.")
        BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
      }
  }

  // ---------- Tags related code ends ------------------

  @ApiOperation(value = "Add comment to file", notes = "", responseClass = "None", httpMethod = "POST")
  def comment(id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateComments)) {
    implicit request =>
      request.user match {
        case Some(identity) => {
          (request.body \ "text").asOpt[String] match {
            case Some(text) => {
              val comment = new Comment(identity, text, file_id = Some(id))
              comments.insert(comment)
              files.index(id)
              Ok(comment.id.toString)
            }
            case None => {
              Logger.error("no text specified.")
              BadRequest(toJson("no text specified."))
            }
          }
        }
        case None =>
          Logger.error(("No user identity found in the request, request body: " + request.body))
          BadRequest(toJson("No user identity found in the request, request body: " + request.body))
      }
  }


  /**
   * Return whether a file is currently being processed.
   */
  @ApiOperation(value = "Is being processed",
      notes = "Return whether a file is currently being processed by a preprocessor.",
      responseClass = "None", httpMethod = "GET")
  def isBeingProcessed(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile), resourceId = Some(id)) {
    request =>
      files.get(id) match {
        case Some(file) => {
          var isActivity = "false"
          extractions.findIfBeingProcessed(file.id) match {
            case false =>
            case true => isActivity = "true"
          }
          Ok(toJson(Map("isBeingProcessed" -> isActivity)))
        }
        case None => {
          Logger.error("Error getting file" + id);
          InternalServerError
        }
      }
  }


  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }

  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(UUID(prv._1), prv._2, prv._3, prv._4, prv._5, prv._6, prv._7, prvFile.id)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }

  def jsonPreview(pvId: UUID, pId: String, pPath: String, pMain: String, pvRoute: java.lang.String, pvContentType: String, pvLength: Long, originalFileId: UUID): JsValue = {
    if (pId.equals("X3d"))
      toJson(Map("pv_id" -> pvId.stringify, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString,
        "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
        "pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(pvId, originalFileId).toString,
        "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(pvId).toString,
        "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(pvId, originalFileId).toString))
    else
      toJson(Map("pv_id" -> pvId.stringify, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString,
        "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))
  }

  @ApiOperation(value = "Get file previews",
      notes = "Return the currently existing previews of the selected file (full description, including paths to preview files, previewer names etc).",
      responseClass = "None", httpMethod = "GET")
  def getPreviews(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile), resourceId = Some(id)) {
	    request =>
	      files.get(id) match {
	        case Some(file) => {

	          val previewsFromDB = previews.findByFileId(file.id)
	          val previewers = Previewers.findPreviewers
	          //Logger.info("Number of previews " + previews.length);
	          val files = List(file)
	          val previewslist = for (f <- files; if (!f.showPreviews.equals("None"))) yield {
	            val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
	              (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
	            }
	            if (pvf.length > 0) {
	              (file -> pvf)
	            } else {
	              val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
	                (file.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(file.id) + "/blob", file.contentType, file.length)
	              }
	              (file -> ff)
	            }
	          }

	          Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]]))
	        }
	        case None => {
	          Logger.error("Error getting file" + id);
	          InternalServerError
	        }
	      }
	  }  
  
      @ApiOperation(value = "Get metadata of the resource described by the file that were input as XML",
	      notes = "",
	      responseClass = "None", httpMethod = "GET")
	  def getXMLMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata), resourceId = Some(id)) { request =>
	    files.get(id)  match {
	      case Some(file) => {
	        Ok(files.getXMLMetadataJSON(id))
	      }
	      case None => {Logger.error("Error finding file" + id); InternalServerError}      
	    }
	  }
	  
	  @ApiOperation(value = "Get user-generated metadata of the resource described by the file",
		      notes = "",
		      responseClass = "None", httpMethod = "GET")
	  def getUserMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata), resourceId = Some(id)) { request =>
	   files.get(id)  match {
	      case Some(file) => {
	        Ok(files.getUserMetadataJSON(id))
	      }
	      case None => {Logger.error("Error finding file" + id); InternalServerError}      
	    }
	  }

	  @ApiOperation(value = "Get technical metadata of the resource described by the file",
		      notes = "",
		      responseClass = "None", httpMethod = "GET")
	  def getTechnicalMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFilesMetadata), resourceId = Some(id)) {
	    request =>
	      files.get(id) match {
	        case Some(file) => {
	          Ok(files.getTechnicalMetadataJSON(id))
	        }
	        case None => {
	          Logger.error("Error finding file" + id);
	          InternalServerError
	        }
	      }
	  }
  
	  @ApiOperation(value = "Delete file",
		      notes = "Cascading action (removes file from any datasets containing it and deletes its previews, metadata and thumbnail).",
		      responseClass = "None", httpMethod = "POST")
	  def removeFile(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DeleteFiles), resourceId = Some(id)) { request =>
	    files.get(id)  match {
	      case Some(file) => {
	        files.removeFile(id)
	        accessRights.removeResourceRightsForAll(id.stringify, "file")
	        Logger.debug(file.filename)

	        //remove file from RDF triple store if triple store is used
		        configuration.getString("userdfSPARQLStore").getOrElse("no") match {
	            case "yes" => {
	              if (file.filename.endsWith(".xml")) {
	                sqarql.removeFileFromGraphs(id, "rdfXMLGraphName")
	              }
	              sqarql.removeFileFromGraphs(id, "rdfCommunityGraphName")
	            }
	            case _ => {}
	          }
	        
	                
	        Ok(toJson(Map("status"->"success")))
	        current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("File","removed",id.stringify, file.filename)}
	        Ok(toJson(Map("status"->"success")))
	      }
	      case None => Ok(toJson(Map("status" -> "success")))
	    }
	  }

  /**
   * List datasets satisfying a user metadata search tree.
   */
  def searchFilesUserMetadata = SecuredAction(authorization = WithPermission(Permission.SearchFiles)) {
    request =>
      Logger.debug("Searching files' user metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = files.searchUserMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning files list.")
      
      var list: List[JsValue] = List.empty
      request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	list = for (file <- searchQuery; if(checkAccessForFileUsingRightsList(file, request.user , "view", rightsForUser))) yield jsonFileWithThumbnail(file, request.user, rightsForUser)
	        }
	        case None=>{
	          list = for (file <- searchQuery; if(checkAccessForFile(file, request.user , "view"))) yield jsonFileWithThumbnail(file, request.user)
	        }
	 }

      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }


  /**
   * List datasets satisfying a general metadata search tree.
   */
  def searchFilesGeneralMetadata = SecuredAction(authorization = WithPermission(Permission.SearchFiles)) {
    request =>
      Logger.debug("Searching files' metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = files.searchAllMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning files list.")
      
      var list: List[JsValue] = List.empty
      request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	list = for (file <- searchQuery; if(checkAccessForFileUsingRightsList(file, request.user , "view", rightsForUser))) yield jsonFileWithThumbnail(file, request.user, rightsForUser)
	        }
	        case None=>{
	          list = for (file <- searchQuery; if(checkAccessForFile(file, request.user , "view"))) yield jsonFileWithThumbnail(file, request.user)
	        }
	 }

      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }
  
  @ApiOperation(value = "Set whether a file is open for public viewing.",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def setIsPublic(id: UUID) = SecuredAction(authorization = WithPermission(Permission.AdministrateFiles), resourceId = Some(id)) {
    request =>
        	(request.body \ "isPublic").asOpt[Boolean].map { isPublic =>
        	  files.get(id)match{
        	    case Some(file)=>{
        	      files.setIsPublic(id, isPublic)
        	      Ok("Done")
        	    }
        	    case None=>{
        	      Logger.error("Error getting file with id " + id.stringify)
                  Ok("No file with supplied id exists.")
        	    }
        	  } 
	       }.getOrElse {
	    	   BadRequest(toJson("Missing parameter [isPublic]"))
	       }
  }


  def index(id: UUID) {
    files.get(id) match {
      case Some(file) => {
        var tagListBuffer = new ListBuffer[String]()

        for (tag <- file.tags) {
          tagListBuffer += tag.name
        }

        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val commentsByFile = for (comment <- comments.findCommentsByFileId(id)) yield comment.text

        val commentJson = new JSONArray(commentsByFile)

        Logger.debug("commentStr=" + commentJson.toString())

        val usrMd = files.getUserMetadataJSON(id)
        Logger.debug("usrmd=" + usrMd)

        val techMd = files.getTechnicalMetadataJSON(id)
        Logger.debug("techmd=" + techMd)

        val xmlMd = files.getXMLMetadataJSON(id)
        Logger.debug("xmlmd=" + xmlMd)

        var fileDsId = ""
        var fileDsName = ""

        for (dataset <- datasets.findByFileId(file.id)) {
          fileDsId = fileDsId + dataset.id.toString + "  "
          fileDsName = fileDsName + dataset.name + "  "
        }

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "file", id,
            List(("filename", file.filename), ("contentType", file.contentType), ("datasetId", fileDsId),
              ("datasetName", fileDsName), ("tag", tagsJson.toString), ("comments", commentJson.toString),
              ("usermetadata", usrMd), ("technicalmetadata", techMd), ("xmlmetadata", xmlMd)))
        }
      }
      case None => Logger.error("File not found: " + id)
    }
  }
  
  
  
  def checkAccessForFile(file: File, user: Option[Identity], permissionType: String): Boolean = {
    if(permissionType.equals("view") && (file.isPublic.getOrElse(false) || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          theUser.fullName.equals("Anonymous User") || appConfiguration.adminExists(theUser.email.getOrElse("")) || file.author.identityId.userId.equals(theUser.identityId.userId) || accessRights.checkForPermission(theUser, file.id.stringify, "file", permissionType)
        }
        case None=>{
          false
        }
      }
    }
  }
  
  def checkAccessForFileUsingRightsList(file: File, user: Option[Identity], permissionType: String, rightsForUser: Option[UserPermissions]): Boolean = {
    if(permissionType.equals("view") && (file.isPublic.getOrElse(false) || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          val canAccessWithoutRightsList = theUser.fullName.equals("Anonymous User") || appConfiguration.adminExists(theUser.email.getOrElse("")) || file.author.identityId.userId.equals(theUser.identityId.userId)
          rightsForUser match{
	        case Some(userRights)=>{
	        	if(canAccessWithoutRightsList)
	        	  true
	        	else{
	        	  if(permissionType.equals("view")){
			        userRights.filesViewOnly.contains(file.id.stringify)
			      }else if(permissionType.equals("modify")){
			        userRights.filesViewModify.contains(file.id.stringify)
			      }else if(permissionType.equals("administrate")){
			        userRights.filesAdministrate.contains(file.id.stringify)
			      }
			      else{
			        Logger.error("Unknown permission type")
			        false
			      }
	        	}
	        }
	        case None=>{
	          canAccessWithoutRightsList
	        }
	      }
        }
        case None=>{
          false
        }
      }
    }
  }
  
  
  
}

object MustBreak extends Exception {}

package controllers

import java.io._
import java.util.Date
import models.{UUID, FileMD, Thumbnail}
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.iteratee._
import services._
import play.api.libs.concurrent.Execution.Implicits._
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import play.api.libs.json.Json._
import fileutils.FilesUtils
import api.WithPermission
import api.Permission
import javax.inject.Inject
import securesocial.core.Identity
import models.UserPermissions

/**
 * Manage files.
 *
 * @author Luigi Marini
 */
class Files @Inject() (
  files: FileService,
  datasets: DatasetService,
  queries: MultimediaQueryService,
  comments: CommentService,
  sections: SectionService,
  extractions: ExtractionService,
  previews: PreviewService,
  threeD: ThreeDService,
  sparql: RdfSPARQLService,
  accessRights: UserAccessRightsService,
  thumbnails: ThumbnailService,
  appConfiguration: AppConfigurationService) extends SecuredController {

  /**
   * Upload form.
   */
  val uploadForm = Form(
    mapping(
      "userid" -> nonEmptyText
    )(FileMD.apply)(FileMD.unapply)
  )

  /**
   * File info.
   */
  def file(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowFile), resourceId = Some(id)) { implicit request =>
    implicit val user = request.user
    Logger.info("GET file with id " + id)
    files.get(id) match {
      case Some(file) => {
        var rightsForUser: Option[models.UserPermissions] = None
        user match{
		        case Some(theUser)=>{
		            rightsForUser = accessRights.get(theUser)
		        }
		        case None=>{
		        }
		}
        
        val previewsFromDB = previews.findByFileId(file.id)
        val previewers = Previewers.findPreviewers
        val previewsWithPreviewer = {
          val pvf = for (p <- previewers; pv <- previewsFromDB; if (!file.showPreviews.equals("None")) && (p.contentType.contains(pv.contentType))) yield {
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
          }
          if (pvf.length > 0) {
            Map(file -> pvf)
          } else {
            val ff = for (p <- previewers; if (!file.showPreviews.equals("None")) && (p.contentType.contains(file.contentType))) yield {
              (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id) + "/blob", file.contentType, file.length)
            }
            Map(file -> ff)
          }
        }
        val sectionsByFile = sections.findByFileId(UUID(file.id.toString))
        val sectionsWithPreviews = sectionsByFile.map { s =>
          val p = previews.findBySectionId(s.id)
          s.copy(preview = Some(p(0)))
        }

        //Search whether file is currently being processed by extractor(s)
        var isActivity = false
        extractions.findIfBeingProcessed(file.id) match {
		      case false =>
		      case true => isActivity = true
        }
        
        val userMetadata = files.getUserMetadata(file.id)
        Logger.debug("User metadata: " + userMetadata.toString)
        
        var commentsByFile = comments.findCommentsByFileId(id)
        sectionsByFile.map { section =>
          commentsByFile ++= comments.findCommentsBySectionId(section.id)
        }
        commentsByFile = commentsByFile.sortBy(_.posted)
        
        var fileDataset: List[models.Dataset] = List.empty
        var datasetsOutside: List[models.Dataset] = List.empty
        
        var datasetsChecker = services.DI.injector.getInstance(classOf[controllers.Datasets])
        		user match{
	        		case Some(theUser)=>{
	        					fileDataset = for(checkedDataset <- datasets.findByFileId(file.id).sortBy(_.name); if(datasetsChecker.checkAccessForDatasetUsingRightsList(checkedDataset, user, "view", rightsForUser))) yield checkedDataset
	        					datasetsOutside = for(checkedDataset <- datasets.findNotContainingFile(file.id).sortBy(_.name); if(datasetsChecker.checkAccessForDatasetUsingRightsList(checkedDataset, user, "modify", rightsForUser))) yield checkedDataset
	        		}
	        		case None=>{
	        					fileDataset = for(checkedDataset <- datasets.findByFileId(file.id).sortBy(_.name); if(datasetsChecker.checkAccessForDataset(checkedDataset, user, "view"))) yield checkedDataset
	        					datasetsOutside = for(checkedDataset <- datasets.findNotContainingFile(file.id).sortBy(_.name); if(datasetsChecker.checkAccessForDataset(checkedDataset, user, "modify"))) yield checkedDataset
	        		}
        		}
        
        val isRDFExportEnabled = current.plugin[RDFExportService].isDefined
        
        Ok(views.html.file(file, id.stringify, commentsByFile, previewsWithPreviewer, sectionsWithPreviews, isActivity, fileDataset, datasetsOutside, userMetadata, isRDFExportEnabled, rightsForUser))
      }
      case None => {
        val error_str = "The file with id " + id + " is not found."
        Logger.error(error_str)
        NotFound(toJson(error_str))
        }
    }
  }

  /**
   * List a specific number of files before or after a certain date.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(authorization = WithPermission(Permission.ListFiles)) { implicit request =>
    implicit val user = request.user
    var rightsForUser: Option[models.UserPermissions] = None
	      user match{
			        case Some(theUser)=>{
			            rightsForUser = accessRights.get(theUser)
			        }
			        case None=>{
			        }
	      }
    
    var direction = "b"
    if (when != "") direction = when
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    var prev, next = ""
    var fileList = List.empty[models.File]
    if (direction == "b") {
      fileList = files.listFilesBefore(date, limit, user)
    } else if (direction == "a") {
      fileList = files.listFilesAfter(date, limit, user)
    } else {
      badRequest
    }
    // latest object
    val latest = files.latest(user)
    // first object
    val first = files.first(user)
    var firstPage = false
    var lastPage = false
    if (latest.size == 1) {
      firstPage = fileList.exists(_.id.equals(latest.get.id))
      lastPage = fileList.exists(_.id.equals(first.get.id))
      Logger.debug("latest " + latest.get.id + " first page " + firstPage)
      Logger.debug("first " + first.get.id + " last page " + lastPage)
    }

    if (fileList.size > 0) {
      if (date != "" && !firstPage) { // show prev button
        prev = formatter.format(fileList.head.uploadDate)
      }
      if (!lastPage) { // show next button
        next = formatter.format(fileList.last.uploadDate)
      }
    }
    Ok(views.html.filesList(fileList, prev, next, limit, rightsForUser))
  }

  /**
   * Upload file page.
   */
  def uploadFile = SecuredAction(authorization = WithPermission(Permission.CreateFiles)) { implicit request =>
    implicit val user = request.user
    Ok(views.html.upload(uploadForm))
  }

  /**
   * Upload file.
   */
  def upload() = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(identity) => {
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

	        var showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)
	        var isPublicOption = request.body.asFormUrlEncoded.get("filePrivatePublic")
	        if(!isPublicOption.isDefined)
	          isPublicOption = Some(List("false"))	        
	        val isPublic = isPublicOption.get(0).toBoolean

	        // store file       
	        val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, identity, showPreviews, isPublic)
	        val uploadedFile = f
	//        Thread.sleep(1000)
	        file match {
	          case Some(f) => {
	        	accessRights.addPermissionLevel(request.user.get, f.id.stringify, "file", "administrate")  
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
				          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped") ){
				            if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
				            
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
	            
	        	if(nameOfFile.startsWith("MEDICI2DATASET_")){
	        		nameOfFile = nameOfFile.replaceFirst("MEDICI2DATASET_","")
	        		files.renameFile(f.id, nameOfFile)
	        	}
	        	else if(nameOfFile.startsWith("MEDICI2MULTISPECTRAL_")){
	        		nameOfFile = nameOfFile.replaceFirst("MEDICI2MULTISPECTRAL_","")
	        		files.renameFile(f.id, nameOfFile)
	        	}
	        	
	            current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
	            
	            // TODO RK need to replace unknown with the server name
	            val key = "unknown." + "file."+ fileType.replace(".","_").replace("/", ".")
	            // TODO RK : need figure out if we can use https
	            val host = "http://" + request.host + request.path.replaceAll("upload$", "")
	            val id = f.id

              // TODO replace null with None
	            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))}
	            
	            val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
	            
	            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(id, xmlToJSON)
	              
	              Logger.debug("xmlmd=" + xmlToJSON)
	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",""),("datasetName",""), ("xmlmetadata", xmlToJSON)))
		            }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",""),("datasetName","")))
		            }
	            }
	            
	          var extractJobId=current.plugin[VersusPlugin].foreach{_.extract(f.id)}
	          
	          Logger.debug("Inside File: Extraction Id : "+ extractJobId)       

	             current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }
	             //current.plugin[VersusPlugin].foreach{_.build()}
	             
	             //add file to RDF triple store if triple store is used
	             if(fileType.equals("application/xml") || fileType.equals("text/xml")){
		             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
			             case "yes" => sparql.addFileToGraph(f.id)
			             case _ => {}		             
		             }
	             }
	                        
	            // redirect to file page]
	            Redirect(routes.Files.file(f.id))
	            current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("File","added",f.id.stringify, nameOfFile)}
	            Redirect(routes.Files.file(f.id))
	         }
	         case None => {
	           Logger.error("Could not retrieve file that was just saved.")
	           InternalServerError("Error uploading file")
	         }
	        }
	      }.getOrElse {
	         BadRequest("File not attached.")
	
	      }
      }
      case None => Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new files.")
    }
  }

  ////////////////////////////////////////////////
  
  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  def download(id: UUID) = SecuredAction(authorization = WithPermission(Permission.DownloadFiles), resourceId = Some(id)) { request =>
    files.getBytes(id) match {
      case Some((inputStream, filename, contentType, contentLength)) => {
        request.headers.get(RANGE) match {
          case Some(value) => {
            val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
              case x => (x(0).toLong, x(1).toLong)
            }
	            range match { case (start,end) =>

                inputStream.skip(start)
                import play.api.mvc.{ SimpleResult, ResponseHeader }
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

  def thumbnail(id: UUID) = SecuredAction(authorization=WithPermission(Permission.ShowFile)) { implicit request =>
    thumbnails.getBlob(id) match {
      case Some((inputStream, filename, contentType, contentLength)) => {
        request.headers.get(RANGE) match {
	          case Some(value) => {
	            val range: (Long,Long) = value.substring("bytes=".length).split("-") match {
	              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
	              case x => (x(0).toLong,x(1).toLong)
	            }
	            range match { case (start,end) =>

	             
	              inputStream.skip(start)
	              import play.api.mvc.{SimpleResult, ResponseHeader}
	              SimpleResult(
	                header = ResponseHeader(PARTIAL_CONTENT,
	                  Map(
	                    CONNECTION -> "keep-alive",
	                    ACCEPT_RANGES -> "bytes",
	                    CONTENT_RANGE -> "bytes %d-%d/%d".format(start,end,contentLength),
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
        Logger.error("Error getting thumbnail" + id)
        NotFound
      }      
    }
    
  }
  
  
  
  def uploadSelect() = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) { implicit request =>
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
        // TODO is this still used? if so replace null with user
        Logger.info("uploadSelect")
        val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, null)
        val uploadedFile = f
        file match {
          case Some(f) => {
             accessRights.addPermissionLevel(request.user.get, f.id.stringify, "file", "administrate")           
             var fileType = f.contentType
			    if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }
			          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped") ){
			            if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
			            
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
             
             if(nameOfFile.startsWith("MEDICI2DATASET_")){
	        		nameOfFile = nameOfFile.replaceFirst("MEDICI2DATASET_","")
	        		files.renameFile(f.id, nameOfFile)
	        	}
	        	else if(nameOfFile.startsWith("MEDICI2MULTISPECTRAL_")){
	        		nameOfFile = nameOfFile.replaceFirst("MEDICI2MULTISPECTRAL_","")
	        		files.renameFile(f.id, nameOfFile)
	        	}
             
             current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
            
            // TODO RK need to replace unknown with the server name
            val key = "unknown." + "file."+ fileType.replace("/", ".")
            // TODO RK : need figure out if we can use https
            val host = "http://" + request.host + request.path.replaceAll("upload$", "")
            val id = f.id
            // TODO replace null with None
            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))}
            
            val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
            
            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(id, xmlToJSON)
	              
	              Logger.debug("xmlmd=" + xmlToJSON)
	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("files", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date())), ("xmlmetadata", xmlToJSON)))
		            }
	            }
	            else {
		            current.plugin[ElasticsearchPlugin].foreach{
		            	_.index("files", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date()))))
		            }
	            }
	            
	            //add file to RDF triple store if triple store is used
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
		             case "yes" => sparql.addFileToGraph(f.id)
		             case _ => {}
	             }
	            }

            // redirect to file page]
            // val query="http://localhost:9000/files/"+id+"/blob"  
           //  var slashindex=query.lastIndexOf('/')
             Redirect(routes.Search.findSimilar(f.id))
         }
          case None => {
            Logger.error("Could not retrieve file that was just saved.")
            InternalServerError("Error uploading file")
          }
        }
    }.getOrElse {
      BadRequest("File not attached.")
    }
  }

  /**
   * Upload query to temporary folder
  */
  def uploadSelectQuery() = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    request.body.file("File").map { f =>
        var nameOfFile = f.filename
      	var flags = ""
      	if(nameOfFile.toLowerCase().endsWith(".ptm")){
      			  var thirdSeparatorIndex = nameOfFile.indexOf("__")
	              if(thirdSeparatorIndex >= 0){
	                var firstSeparatorIndex = nameOfFile.indexOf("_")
	                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
	            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" +
                        nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" +
                        nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
	            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
	              }
      	}
        
        Logger.debug("Uploading file " + nameOfFile)
        
        // store file       
        Logger.info("uploadSelectQuery")
         val file = queries.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType)
         val uploadedFile = f
        
        file match {
          case Some(f) => {
            accessRights.addPermissionLevel(request.user.get, f.id.stringify, "file", "administrate")           
            var fileType = f.contentType
			    if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }
			          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped") ){
			            if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
			            
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
            
            if(nameOfFile.startsWith("MEDICI2DATASET_")){
	        		nameOfFile = nameOfFile.replaceFirst("MEDICI2DATASET_","")
	        		files.renameFile(f.id, nameOfFile)
	        	}
	        	else if(nameOfFile.startsWith("MEDICI2MULTISPECTRAL_")){
	        		nameOfFile = nameOfFile.replaceFirst("MEDICI2MULTISPECTRAL_","")
	        		files.renameFile(f.id, nameOfFile)
	        	}
            
            current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
            
            // TODO RK need to replace unknown with the server name
            val key = "unknown." + "file."+ fileType.replace("/", ".")
            // TODO RK : need figure out if we can use https
            val host = "http://" + request.host + request.path.replaceAll("upload$", "")
            
            val id = f.id
            val path=f.path

            // TODO replace null with None
            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))}
            
            val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
            
            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(id, xmlToJSON)
	              
	              Logger.debug("xmlmd=" + xmlToJSON)
	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("files", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date())), ("xmlmetadata", xmlToJSON)))
		            }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		            	_.index("files", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date()))))
		            }
	            }
	            
	            //add file to RDF triple store if triple store is used
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
		             case "yes" => sparql.addFileToGraph(f.id)
		             case _ => {}
	             }
	            }
            
            // redirect to file page]
            Logger.debug("Query file id= "+id+ " path= "+path);
             Redirect(routes.Search.findSimilar(f.id))
             //Redirect(routes.Search.findSimilar(path.toString())) 
         }
          case None => {
            Logger.error("Could not retrieve file that was just saved.")
            InternalServerError("Error uploading file")
          }
        }
    }.getOrElse {
      BadRequest("File not attached.")
    }
  }

  /* Drag and drop */
  def uploadDragDrop() = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
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
        Logger.info("uploadDragDrop")
        val file = queries.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType)
        val uploadedFile = f
        file match {
          case Some(f) => {
             accessRights.addPermissionLevel(request.user.get, f.id.stringify, "file", "administrate")          
             var fileType = f.contentType
			    if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }
			          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped") ){
			            if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
			            
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
             
             if(nameOfFile.startsWith("MEDICI2DATASET_")){
	        		nameOfFile = nameOfFile.replaceFirst("MEDICI2DATASET_","")
	        		files.renameFile(f.id, nameOfFile)
	        	}
	        	else if(nameOfFile.startsWith("MEDICI2MULTISPECTRAL_")){
	        		nameOfFile = nameOfFile.replaceFirst("MEDICI2MULTISPECTRAL_","")
	        		files.renameFile(f.id, nameOfFile)
	        	}
             
             current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
            
            // TODO RK need to replace unknown with the server name
            val key = "unknown." + "file."+ fileType.replace(".","_").replace("/", ".")
            // TODO RK : need figure out if we can use https
            val host = "http://" + request.host + request.path.replaceAll("upload$", "")
            val id = f.id

            // TODO replace null with None
            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))}
            
            val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
            
            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(id, xmlToJSON)
	              
	              Logger.debug("xmlmd=" + xmlToJSON)
	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date())), ("xmlmetadata", xmlToJSON)))
		            }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		            	_.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date()))))
		            }
	            }
	            
	            //add file to RDF triple store if triple store is used
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
		             case "yes" => sparql.addFileToGraph(f.id)
		             case _ => {}
	             }
	            }
            
           Ok(f.id.toString)
            
            // redirect to file page]
           // Redirect(routes.Files.file(f.id.toString))  
         }
          case None => {
            Logger.error("Could not retrieve file that was just saved.")
            InternalServerError("Error uploading file")
          }
        }
    }.getOrElse {
      BadRequest("File not attached.")
    }
  }

  def uploaddnd(dataset_id: UUID) = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateDatasets), resourceId = Some(dataset_id)) { implicit request =>
    request.user match {
      case Some(identity) => {
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
				  val showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)
				  var isPublicOption = request.body.asFormUrlEncoded.get("filePrivatePublic")
		        if(!isPublicOption.isDefined)
		          isPublicOption = Some(List("false"))	        
		        val isPublic = isPublicOption.get(0).toBoolean
				  
				  // store file
				  val file = files.save(new FileInputStream(f.ref.file), nameOfFile,f.contentType, identity, showPreviews, isPublic)
				  val uploadedFile = f
				  
				  // submit file for extraction			
				  file match {
				  case Some(f) => {
				    accessRights.addPermissionLevel(request.user.get, f.id.stringify, "file", "administrate")				    
	                if(showPreviews.equals("FileLevel"))
	                	flags = flags + "+filelevelshowpreviews"
	                else if(showPreviews.equals("None"))
	                	flags = flags + "+nopreviews"
					  var fileType = f.contentType
					  if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
						  fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "dataset")			          
						  if(fileType.startsWith("ERROR: ")){
								Logger.error(fileType.substring(7))
								InternalServerError(fileType.substring(7))
								}
						  if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped") ){
						    if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
						    
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
	                
				    if(nameOfFile.startsWith("MEDICI2DATASET_")){
		        		nameOfFile = nameOfFile.replaceFirst("MEDICI2DATASET_","")
		        		files.renameFile(f.id, nameOfFile)
		        	}
		        	else if(nameOfFile.startsWith("MEDICI2MULTISPECTRAL_")){
		        		nameOfFile = nameOfFile.replaceFirst("MEDICI2MULTISPECTRAL_","")
		        		files.renameFile(f.id, nameOfFile)
		        	}
				    
	                current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
				  	  
					  // TODO RK need to replace unknown with the server name
					  val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")
							  // TODO RK : need figure out if we can use https
							  val host = "http://" + request.host + request.path.replaceAll("uploaddnd/[A-Za-z0-9_]*$", "")
							  val id = f.id

							  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dataset_id, flags))}
					  
					  val dateFormat = new SimpleDateFormat("dd/MM/yyyy")

					  
					  //for metadata files
					  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
						  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
								  files.addXMLMetadata(id, xmlToJSON)

								  Logger.debug("xmlmd=" + xmlToJSON)

								  current.plugin[ElasticsearchPlugin].foreach{
						  			  _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dataset.id.toString()),("datasetName",dataset.name), ("xmlmetadata", xmlToJSON)))
						  		  }
					  }
					  else{
						  current.plugin[ElasticsearchPlugin].foreach{
							  _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dataset.id.toString()),("datasetName",dataset.name)))
						  }
					  }
					  
					  // add file to dataset
					  // TODO create a service instead of calling salat directly
					  val theFile = files.get(f.id).get
					  datasets.addFile(dataset.id, theFile)
					  datasets.index(dataset_id)
					  
					// TODO RK need to replace unknown with the server name and dataset type
 			    	val dtkey = "unknown." + "dataset."+ "unknown"
			    	
			        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dataset_id, dataset_id, host, dtkey, Map.empty, f.length.toString, dataset_id, ""))}
 			    	
 			    	//add file to RDF triple store if triple store is used
 			    	if(fileType.equals("application/xml") || fileType.equals("text/xml")){
		             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
			             case "yes" => {
                     sparql.addFileToGraph(f.id)
                     sparql.linkFileToDataset(f.id, dataset_id)
			             }
			             case _ => {}
		             }
 			    	}
		
					  // redirect to dataset page
					  Logger.info("Uploading Completed")
					  
					  Redirect(routes.Datasets.dataset(dataset_id))
				  	}
				  	case None => {
					  Logger.error("Could not retrieve file that was just saved.")
					  InternalServerError("Error uploading file")
				  	}
				  }
			  }.getOrElse {
				  BadRequest("File not attached.")
			  }
		  }
		  case None => {Logger.error("Error getting dataset" + dataset_id); InternalServerError}
      	}
      }
      case None => { Logger.error("Error getting dataset" + dataset_id); InternalServerError }
    }
  }

  def metadataSearch()  = SecuredAction(authorization=WithPermission(Permission.SearchFiles)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.fileMetadataSearch()) 
  }

  def generalMetadataSearch()  = SecuredAction(authorization=WithPermission(Permission.SearchFiles)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.fileGeneralMetadataSearch()) 
  }
  
  
  def checkAccessForFile(file: models.File, user: Option[Identity], permissionType: String): Boolean = {
    if(permissionType.equals("view") && (file.isPublic.getOrElse(false) || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          appConfiguration.adminExists(theUser.email.getOrElse("")) || file.author.identityId.userId.equals(theUser.identityId.userId) || accessRights.checkForPermission(theUser, file.id.stringify, "file", permissionType)
        }
        case None=>{
          false
        }
      }
    }
  }
  
  def checkAccessForFileUsingRightsList(file: models.File, user: Option[Identity], permissionType: String, rightsForUser: Option[UserPermissions]): Boolean = {
    if(permissionType.equals("view") && (file.isPublic.getOrElse(false) || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          val canAccessWithoutRightsList =  appConfiguration.adminExists(theUser.email.getOrElse("")) || file.author.identityId.userId.equals(theUser.identityId.userId)
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
  

  ///////////////////////////////////
  //
  //  def myPartHandler: BodyParsers.parse.Multipart.PartHandler[MultipartFormData.FilePart[Result]] = {
  //        parse.Multipart.handleFilePart {
  //          case parse.Multipart.FileInfo(partName, filename, contentType) =>
  //            Logger.info("Part: " + partName + " filename: " + filename + " contentType: " + contentType);
  //            // TODO RK handle exception for instance if we switch to other DB
  //        Logger.info("myPartHandler")
  //			val files = current.plugin[MongoSalatPlugin] match {
  //			  case None    => throw new RuntimeException("No MongoSalatPlugin");
  //			  case Some(x) =>  x.gridFS("uploads")
  //			}
  //            
  //            //Set up the PipedOutputStream here, give the input stream to a worker thread
  //            val pos:PipedOutputStream = new PipedOutputStream();
  //            val pis:PipedInputStream  = new PipedInputStream(pos);
  //            val worker = new util.UploadFileWorker(pis, files);
  //            worker.contentType = contentType.get;
  //            worker.start();
  //
  ////            val mongoFile = files.createFile(f.ref.file)
  ////            val filename = f.ref.file.getName()
  ////            Logger.debug("Uploading file " + filename)
  ////            mongoFile.filename = filename
  ////            mongoFile.contentType = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
  ////            mongoFile.save
  ////            val id = mongoFile.getAs[ObjectId]("_id").get.toString
  ////            Ok(views.html.file(mongoFile.asDBObject, id))
  //            
  //            
  //            //Read content to the POS
  //            Iteratee.fold[Array[Byte], PipedOutputStream](pos) { (os, data) =>
  //              os.write(data)
  //              os
  //            }.mapDone { os =>
  //              os.close()
  //              Ok("upload done")
  //            }
  //        }
  //   }
  //  
  //  /**
  //   * Ajax upload. How do we pass in the file name?(parse.temporaryFile)
  //   */
  //  
  //  
  //  def uploadAjax = Action(parse.temporaryFile) { request =>
  //
  //    val f = request.body.file
  //    val filename=f.getName()
  //    
  //    // store file
  //    // TODO is this still used? if so replace null with user.
  //        Logger.info("uploadAjax")
  //    val file = files.save(new FileInputStream(f.getAbsoluteFile()), filename, None, null)
  //    
  //    file match {
  //      case Some(f) => {
  //         var fileType = f.contentType
  //        
  //        // TODO RK need to replace unknown with the server name
  //        val key = "unknown." + "file."+ f.contentType.replace(".", "_").replace("/", ".")
  //        // TODO RK : need figure out if we can use https
  //        val host = "http://" + request.host + request.path.replaceAll("upload$", "")
  //        val id = f.id.toString
  //        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", ""))}
  //        current.plugin[ElasticsearchPlugin].foreach{
  //          _.index("files", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
  //        }
  //        // redirect to file page
  //        Redirect(routes.Files.file(f.id.toString))  
  //      }
  //      case None => {
  //        Logger.error("Could not retrieve file that was just saved.")
  //        InternalServerError("Error uploading file")
  //      }
  //    }
  //  }

  /**
   * Reactive file upload.
   */
  //  def reactiveUpload = Action(BodyParser(rh => new SomeIteratee)) { request =>
  //     Ok("Done")
  //   }

  /**
   * Iteratee for reactive file upload.
   *
   * TODO Finish implementing. Right now it doesn't write to anything.
   */
  // case class SomeIteratee(state: Symbol = 'Cont, input: Input[Array[Byte]] = Empty, 
  //     received: Int = 0) extends Iteratee[Array[Byte], Either[Result, Int]] {
  //   Logger.debug(state + " " + input + " " + received)
  //
  ////   val files = current.plugin[MongoSalatPlugin] match {
  ////			  case None    => throw new RuntimeException("No MongoSalatPlugin");
  ////			  case Some(x) =>  x.gridFS("uploads")
  ////			}
  ////
  ////   val pos:PipedOutputStream = new PipedOutputStream();
  ////   val pis:PipedInputStream  = new PipedInputStream(pos);
  ////   val file = files(pis) { fh =>
  ////     fh.filename = "test-file.txt"
  ////     fh.contentType = "text/plain"
  ////   }
  //			
  //   
  //   def fold[B](
  //     done: (Either[Result, Int], Input[Array[Byte]]) => Promise[B],
  //     cont: (Input[Array[Byte]] => Iteratee[Array[Byte], Either[Result, Int]]) => Promise[B],
  //     error: (String, Input[Array[Byte]]) => Promise[B]
  //   ): Promise[B] = state match {
  //     case 'Done => { 
  //       Logger.debug("Done with upload")
  ////       pos.close()
  //       done(Right(received), Input.Empty) 
  //     }
  //     case 'Cont => cont(in => in match {
  //       case in: El[Array[Byte]] => {
  //         Logger.debug("Getting ready to write " +  in.e.length)
  //    	 try {
  ////         pos.write(in.e)
  //    	 } catch {
  //    	   case error => Logger.error("Error writing to gridfs" + error.toString())
  //    	 }
  //    	 Logger.debug("Calling recursive function")
  //         copy(input = in, received = received + in.e.length)
  //       }
  //       case Empty => {
  //         Logger.debug("Empty")
  //         copy(input = in)
  //       }
  //       case EOF => {
  //         Logger.debug("EOF")
  //         copy(state = 'Done, input = in)
  //       }
  //       case _ => {
  //         Logger.debug("_")
  //         copy(state = 'Error, input = in)
  //       }
  //     })
  //     case _ => { Logger.error("Error uploading file"); error("Some error.", input) }
  //   }
  // }
}

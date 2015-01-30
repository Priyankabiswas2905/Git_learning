package controllers

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Cookie
import java.io.FileInputStream
import play.api.Play.current
import services._
import java.util.Date
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import models._
import fileutils.FilesUtils
import api.Permission
import javax.inject.Inject
import scala.Some
import scala.xml.Utility
import services.ExtractorMessage
import api.WithPermission
import org.apache.commons.lang.StringEscapeUtils


/**
 * A dataset is a collection of files and streams.
 *
 * @author Luigi Marini
 *
 */
class Datasets @Inject()(
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  comments: CommentService,
  sections: SectionService,
  extractions: ExtractionService,
  dtsrequests:ExtractionRequestsService,
  sparql: RdfSPARQLService,
  users: UserService,
  previewService: PreviewService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
   * New dataset form.
   */
  val datasetForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
      //No LicenseData needed here, as on creation, default arg handles it. MMF - 5/2014
      ((name, description) => Dataset(name = name, description = description, created = new Date, author = null))
      ((dataset: Dataset) => Some((dataset.name, dataset.description)))
  )

  def newDataset() = SecuredAction(authorization = WithPermission(Permission.CreateDatasets)) {
    implicit request =>
      implicit val user = request.user
      val filesList = for (file <- files.listFilesNotIntermediate.sortBy(_.filename)) yield (file.id.toString(), file.filename)
      Ok(views.html.newDataset(datasetForm, filesList)).flashing("error" -> "Please select ONE file (upload new or existing)")
  }

  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int, mode: String) = SecuredAction(authorization = WithPermission(Permission.ListDatasets)) {
    implicit request =>      
      implicit val user = request.user
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
      var prev, next = ""
      var datasetList = List.empty[models.Dataset]
      if (direction == "b") {
        datasetList = datasets.listDatasetsBefore(date, limit)
      } else if (direction == "a") {
        datasetList = datasets.listDatasetsAfter(date, limit)
      } else {
        badRequest
      }
      
      // latest object
      val latest = datasets.latest()
      // first object
      val first = datasets.first()
      var firstPage = false
      var lastPage = false
      if (latest.size == 1) {
        firstPage = datasetList.exists(_.id.equals(latest.get.id))
        lastPage = datasetList.exists(_.id.equals(first.get.id))
        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
        Logger.debug("first " + first.get.id + " last page " + lastPage)
      }
      if (datasetList.size > 0) {
        if (date != "" && !firstPage) {
          // show prev button
          prev = formatter.format(datasetList.head.created)
        }
        if (!lastPage) {
          // show next button
          next = formatter.format(datasetList.last.created)
        }
      }

      val commentMap = datasetList.map{dataset =>
        var allComments = comments.findCommentsByDatasetId(dataset.id)
        dataset.files.map { file =>
          allComments ++= comments.findCommentsByFileId(file.id)
          sections.findByFileId(file.id).map { section =>
            allComments ++= comments.findCommentsBySectionId(section.id)
          }
        }
        dataset.id -> allComments.size
      }.toMap


      //Modifications to decode HTML entities that were stored in an encoded fashion as part 
      //of the datasets names or descriptions
      val aBuilder = new StringBuilder()
      for (aDataset <- datasetList) {
          decodeDatasetElements(aDataset)
      }
      
        //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
	    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar 
	    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14	
		var viewMode = mode;
		//Always check to see if there is a session value          
		request.cookies.get("view-mode") match {
	    	case Some(cookie) => {
	    		viewMode = cookie.value
	    	}
	    	case None => {
	    		//If there is no cookie, and a mode was not passed in, default it to tile
	    	    if (viewMode == null || viewMode == "") {
	    	        viewMode = "tile"
	    	    }
	    	}
		}
      
      //Pass the viewMode into the view
      Ok(views.html.datasetList(datasetList, commentMap, prev, next, limit, viewMode))
  }
  def userDatasets(when: String, date: String, limit: Int, mode: String, email: String) = SecuredAction(authorization = WithPermission(Permission.ListDatasets)) {
    implicit request =>
      implicit val user = request.user
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
      var prev, next = ""
      var datasetList = List.empty[models.Dataset]
      if (direction == "b") {
        datasetList = datasets.listDatasetsBefore(date, limit)
      } else if (direction == "a") {
        datasetList = datasets.listDatasetsAfter(date, limit)
      } else {
        badRequest
      }
      // latest object
      val latest = datasets.latest()
      // first object
      val first = datasets.first()
      var firstPage = false
      var lastPage = false
      if (latest.size == 1) {
        firstPage = datasetList.exists(_.id.equals(latest.get.id))
        lastPage = datasetList.exists(_.id.equals(first.get.id))
        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
        Logger.debug("first " + first.get.id + " last page " + lastPage)
      }
      if (datasetList.size > 0) {
        if (date != "" && !firstPage) {
          // show prev button
          prev = formatter.format(datasetList.head.created)
        }
        if (!lastPage) {
          // show next button
          next = formatter.format(datasetList.last.created)
        }
      }
      
      datasetList= datasetList.filter(x=> x.author.email.toString == "Some(" +email +")")

      

      val commentMap = datasetList.map{dataset =>
        var allComments = comments.findCommentsByDatasetId(dataset.id)
        dataset.files.map { file =>
          allComments ++= comments.findCommentsByFileId(file.id)
          sections.findByFileId(file.id).map { section =>
            allComments ++= comments.findCommentsBySectionId(section.id)
          }
        }
        dataset.id -> allComments.size
      }.toMap


      //Modifications to decode HTML entities that were stored in an encoded fashion as part 
      //of the datasets names or descriptions
      val aBuilder = new StringBuilder()
      for (aDataset <- datasetList) {
          decodeDatasetElements(aDataset)
      }
      
      //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
      //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar 
      //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14      
      var viewMode = mode;
      
      //Always check to see if there is a session value          
      request.cookies.get("view-mode") match {
          case Some(cookie) => {                  
              viewMode = cookie.value
          }
          case None => {
              //If there is no cookie, and viewMode is not set, default it to tile
              if (viewMode == null || viewMode == "") {
                  viewMode = "tile"
              }
          }
      }                       
      
      Ok(views.html.datasetList(datasetList, commentMap, prev, next, limit, viewMode))
  }


  def addViewer(id: UUID, user: Option[securesocial.core.Identity]) = {
      user match{
            case Some(viewer) => {
              implicit val email = viewer.email
              email match {
                case Some(addr) => {
                  implicit val modeluser = users.findByEmail(addr.toString())
                  modeluser match {
                    case Some(muser) => {
                       muser.viewed match {
                        case Some(viewList) =>{
                          users.addUserDatasetView(addr, id)
                        }
                        case None => {
                          val newList: List[UUID] = List(id)
                          users.createNewListInUser(addr, "viewed", newList)
                        }
                      }
                  }
                  case None => {
                    Ok("NOT WORKS")
                  }
                 }
                }
              }
            }


          }
  }

  /**
   * Dataset.
   */
  def dataset(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowDataset)) {
	  implicit request =>
	      implicit val user = request.user
	      Previewers.findPreviewers.foreach(p => Logger.info("Previewer found " + p.id))
	      datasets.get(id) match {
	        case Some(dataset) => {
	          val filesInDataset = dataset.files.map(f => files.get(f.id).get)

	          //Search whether dataset is currently being processed by extractor(s)
	          var isActivity = false
	          try {
	            for (f <- filesInDataset) {
	              extractions.findIfBeingProcessed(f.id) match {
	                case false =>
	                case true => isActivity = true; throw ActivityFound
	              }
	            }
	          } catch {
	            case ActivityFound =>
	          }

          val datasetWithFiles = dataset.copy(files = filesInDataset)
          decodeDatasetElements(datasetWithFiles)
          val previewers = Previewers.findPreviewers
          //NOTE Should the following code be unified somewhere since it is duplicated in Datasets and Files for both api and controllers
          val previewslist = for (f <- datasetWithFiles.files) yield {


            // add sections to file
            val sectionsByFile = sections.findByFileId(f.id)
            Logger.debug("Sections: " + sectionsByFile)
            val sectionsWithPreviews = sectionsByFile.map { s =>
              val p = previewService.findBySectionId(s.id)
              if(p.length>0)
                s.copy(preview = Some(p(0)))
              else
                s.copy(preview = None)
            }
            Logger.debug("Sections available: " + sectionsWithPreviews)
            val fileWithSections = f.copy(sections = sectionsWithPreviews)


            val pvf = for (p <- previewers; pv <- fileWithSections.previews; if (fileWithSections.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(pv.contentType))) yield {
              (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
            }
            if (pvf.length > 0) {
              fileWithSections -> pvf
            } else {
              val ff = for (p <- previewers; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(f.contentType))) yield {
                //Change here. If the license allows the file to be downloaded by the current user, go ahead and use the 
                //file bytes as the preview, otherwise return the String null and handle it appropriately on the front end
                if (f.checkLicenseForDownload(user)) {
                    (f.id.toString, p.id, p.path, p.main, routes.Files.download(f.id).toString, f.contentType, f.length)
                }
                else {
                    (f.id.toString, p.id, p.path, p.main, "null", f.contentType, f.length)
                }
              }
              fileWithSections -> ff
            }
          }

          val metadata = datasets.getMetadata(id)
          Logger.debug("Metadata: " + metadata)
          for (md <- metadata) {
            Logger.debug(md.toString)
          }
          val userMetadata = datasets.getUserMetadata(id)
          Logger.debug("User metadata: " + userMetadata.toString)

	          val collectionsOutside = collections.listOutsideDataset(id).sortBy(_.name)
	          val collectionsInside = collections.listInsideDataset(id).sortBy(_.name)
	          val filesOutside = files.listOutsideDataset(id).sortBy(_.filename)

	          var commentsByDataset = comments.findCommentsByDatasetId(id)
	          filesInDataset.map {
	            file =>
	              commentsByDataset ++= comments.findCommentsByFileId(file.id)
	              sections.findByFileId(UUID(file.id.toString)).map { section =>
	                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
	              }
	          }
	          commentsByDataset = commentsByDataset.sortBy(_.posted)
	          
	          val isRDFExportEnabled = current.plugin[RDFExportService].isDefined

          Ok(views.html.dataset(datasetWithFiles, commentsByDataset, previewslist.toMap, metadata, userMetadata, isActivity, collectionsOutside, collectionsInside, filesOutside, isRDFExportEnabled))
        }
        case None => {
          Logger.error("Error getting dataset" + id); InternalServerError
        }
      }
  }
  
  /**
   * Utility method to modify the elements in a dataset that are encoded when submitted and stored. These elements
   * are decoded when a view requests the objects, so that they can be human readable.
   * 
   * Currently, the following dataset elements are encoded:
   * name
   * description
   *  
   */
  def decodeDatasetElements(dataset: Dataset) {      
      dataset.name = StringEscapeUtils.unescapeHtml(dataset.name)
      dataset.description = StringEscapeUtils.unescapeHtml(dataset.description)
  }

  /**
   * 3D Dataset.
   */
  def datasetThreeDim(id: UUID) = SecuredAction(authorization=WithPermission(Permission.ShowDataset)) { implicit request =>
    implicit val user = request.user    
    Previewers.findPreviewers.foreach(p => Logger.info("Previewer found " + p.id))
    datasets.get(id)  match {
      case Some(dataset) => {
        val filesInDataset = dataset.files map { f =>{
        		files.get(f.id).get
        	}
        }
        
        //Search whether dataset is currently being processed by extractor(s)
        var isActivity = false
        try{
        	for(f <- filesInDataset){
        		extractions.findIfBeingProcessed(f.id) match{
        			case false => 
        			case true => { 
        				isActivity = true
        				throw ActivityFound
        			  }       
        		}
        	}
        }catch{
          case ActivityFound =>
        }
        
        
        val datasetWithFiles = dataset.copy(files = filesInDataset)
        val previewers = Previewers.findPreviewers
        val previewslist = for(f <- datasetWithFiles.files) yield {          
          val pvf = for(p <- previewers ; pv <- f.previews; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(pv.contentType))) yield { 
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
          }         
          if (pvf.length > 0) {
            (f -> pvf)
          } else {
  	        val ff = for(p <- previewers ; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(f.contentType))) yield {
  	          (f.id.toString, p.id, p.path, p.main, routes.Files.download(f.id).toString, f.contentType, f.length)
  	        }
  	        (f -> ff)
          }
        }
        val previews = Map(previewslist:_*)
        val metadata = datasets.getMetadata(id)
        Logger.debug("Metadata: " + metadata)
        for (md <- metadata) {
          Logger.debug(md.toString)
        }       
        val userMetadata = datasets.getUserMetadata(id)
        Logger.debug("User metadata: " + userMetadata.toString)
        
        val collectionsOutside = collections.listOutsideDataset(id).sortBy(_.name)
        val collectionsInside = collections.listInsideDataset(id).sortBy(_.name)
        val filesOutside = files.listOutsideDataset(id).sortBy(_.filename)
        
        var commentsByDataset = comments.findCommentsByDatasetId(id)
        filesInDataset.map { file =>
          commentsByDataset ++= comments.findCommentsByFileId(file.id)
          sections.findByFileId(UUID(file.id.toString)).map { section =>
          commentsByDataset ++= comments.findCommentsBySectionId(section.id)
          } 
        }
        commentsByDataset = commentsByDataset.sortBy(_.posted)
        
        Ok(views.html.datasetThreeDim(datasetWithFiles, commentsByDataset, previews, metadata, userMetadata, isActivity, collectionsOutside, collectionsInside, filesOutside))
      }
      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
    }
  }
  
  /**
   * Dataset by section.
   */
  def datasetBySection(section_id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowDataset)) {
    request =>
      sections.get(section_id) match {
        case Some(section) => {
          datasets.findOneByFileId(section.file_id) match {
            case Some(dataset) => Redirect(routes.Datasets.dataset(dataset.id))
            case None => InternalServerError("Dataset not found")
          }
        }
        case None => InternalServerError("Section not found")
      }
  }

  /**
   * TODO where is this used?
  def upload = Action(parse.temporaryFile) { request =>
    request.body.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }
   */

  /**
   * Upload file.
   */
	def submit() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateDatasets)) { implicit request =>
    implicit val user = request.user
    
    user match {
      case Some(identity) => {
        datasetForm.bindFromRequest.fold(
          errors => BadRequest(views.html.newDataset(errors, for(file <- files.listFilesNotIntermediate.sortBy(_.filename)) yield (file.id.toString(), file.filename))),
	      dataset => {
	           request.body.file("file").map { f =>
	             //Uploaded file selected
	             
	             //Can't have both an uploaded file and a selected existing file
	             request.body.asFormUrlEncoded.get("existingFile").get(0).equals("__nofile") match{
	               case true => {
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
	            	    Logger.info("Adding file" + identity)
	            	    val showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)
	            	    val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, identity, showPreviews)
					    Logger.debug("Uploaded file id is " + file.get.id)
					    Logger.debug("Uploaded file type is " + f.contentType)
					    
					    val uploadedFile = f
					    file match {
					      case Some(f) => {
					        					        
					        val id = f.id	                	                
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
					        } else if(nameOfFile.toLowerCase().endsWith(".mov")) {
							  		fileType = "ambiguous/mov";
					        }
					        
					        current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
					        
					    		// TODO RK need to replace unknown with the server name
					    		val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")

									val host = Utils.baseUrl(request) + request.path.replaceAll("dataset/submit$", "")

					        // add file to dataset 
					        val dt = dataset.copy(files = List(f), author=identity)					        
					        // TODO create a service instead of calling salat directly
				            datasets.update(dt)				            

						        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dt.id, flags))}
						        //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType)))}
					        
					        val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
					        
					        //for metadata files
							  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
								  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
										  files.addXMLMetadata(f.id, xmlToJSON)
										  datasets.addXMLMetadata(dt.id, f.id, xmlToJSON)
		
										  Logger.debug("xmlmd=" + xmlToJSON)
										  
										  //index the file
										  current.plugin[ElasticsearchPlugin].foreach{
								  			  _.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dt.id.toString()),("datasetName",dt.name), ("xmlmetadata", xmlToJSON)))

								  		  }
								  		  // index dataset
								  		  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
								  		  List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName",""), ("xmlmetadata", xmlToJSON)))}
							  } else {
								  //index the file

								  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dt.id.toString),("datasetName",dt.name)))}

								  // index dataset
								  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
								  List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName","")))}
							  }
					        
					      // index the file using Versus for content-based retrieval
					      current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }

					    	// TODO RK need to replace unknown with the server name and dataset type		            
		 			    	val dtkey = "unknown." + "dataset."+ "unknown"
									current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id, dt.id, host, dtkey, Map.empty, "0", dt.id, ""))}
		 			    	
		 			    	//add file to RDF triple store if triple store is used
		 			    	if(fileType.equals("application/xml") || fileType.equals("text/xml")){
					             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
						             case "yes" => {
						               sparql.addFileToGraph(f.id)
						               sparql.linkFileToDataset(f.id, dt.id)
						             }
						             case _ => {}
					             }
				             }

		 			    	// Insert DTS Request to the database
		 			    	val clientIP=request.remoteAddress
		 			    	val serverIP= request.host
		 			    	dtsrequests.insertRequest(serverIP,clientIP, f.filename, id, fileType, f.length,f.uploadDate)
		 			    	
				            // redirect to dataset page
				            Redirect(routes.Datasets.dataset(dt.id))
					      }
					      
					      case None => {
					        Logger.error("Could not retrieve file that was just saved.")
					        // TODO create a service instead of calling salat directly
					        val dt = dataset.copy(author=identity)
				            datasets.update(dt) 
				            // redirect to dataset page
				            Redirect(routes.Datasets.dataset(dt.id))
				            current.plugin[AdminsNotifierPlugin].foreach{
                      _.sendAdminsNotification(Utils.baseUrl(request), "Dataset","added",dt.id.stringify, dt.name)}
				            Redirect(routes.Datasets.dataset(dt.id))
					      }
					    }   	                 
	                 }
	               case false => Redirect(routes.Datasets.newDataset()).flashing("error"->"Please select ONE file (upload new or existing)")	
	               }
	             
	           
	        }.getOrElse{
	          val fileId = request.body.asFormUrlEncoded.get("existingFile").get(0)
	          fileId match{
	            case "__nofile" => Redirect(routes.Datasets.newDataset()).flashing("error"->"Please select ONE file (upload new or existing)")
	            case _ => {
	              //Existing file selected	          
	          
		          // add file to dataset 
		          val theFile = files.get(UUID(fileId))
		          if(theFile.isEmpty)
		            Redirect(routes.Datasets.newDataset()).flashing("error"->"Selected file not found. Maybe it was removed.")		            
		          val theFileGet = theFile.get
		          
		          val thisFileThumbnail: Option[String] = theFileGet.thumbnail_id
		          var thisFileThumbnailString: Option[String] = None
		          if(!thisFileThumbnail.isEmpty)
		            thisFileThumbnailString = Some(thisFileThumbnail.get)
		          
				  val dt = dataset.copy(files = List(theFileGet), author=identity, thumbnail_id=thisFileThumbnailString)
				  datasets.update(dt)
		  
				  val dateFormat = new SimpleDateFormat("dd/MM/yyyy")

					if(!theFileGet.xmlMetadata.isEmpty){
						val xmlToJSON = files.getXMLMetadataJSON(UUID(fileId))
						datasets.addXMLMetadata(dt.id, UUID(fileId), xmlToJSON)
						// index dataset
						current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id,
					List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",theFileGet.id.toString),("fileName",theFileGet.filename), ("collId",""),("collName",""), ("xmlmetadata", xmlToJSON)))}
					}else{
						// index dataset
						current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id,
					 List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",theFileGet.id.toString),("fileName",theFileGet.filename), ("collId",""),("collName","")))}
					}

					//reindex file
					files.index(theFileGet.id)
					val host = Utils.baseUrl(request) + request.path.replaceAll("dataset/submit$", "")
					// TODO RK need to replace unknown with the server name and dataset type
					val dtkey = "unknown." + "dataset."+ "unknown"

				  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id, dt.id, host, dtkey, Map.empty, "0", dt.id, ""))}

		      //link file to dataset in RDF triple store if triple store is used
		      if(theFileGet.filename.endsWith(".xml")) {
						play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match {
							case "yes" => {
								sparql.linkFileToDataset(UUID(fileId), dt.id)
							}
							case _ => {}
						}
				  }
		          
					// Inserting DTS Requests
					Logger.debug("The file already exists")
					val clientIP=request.remoteAddress
					val domain=request.domain
					val keysHeader=request.headers.keys
					Logger.debug("clientIP:"+clientIP+ "   domain:= "+domain+ "  keysHeader="+ keysHeader.toString +"\n")
					val serverIP= request.host
					dtsrequests.insertRequest(serverIP,clientIP, theFileGet.filename, theFileGet.id, theFileGet.contentType, theFileGet.length,theFileGet.uploadDate)
		          
				  // redirect to dataset page
				  Redirect(routes.Datasets.dataset(dt.id))
				  current.plugin[AdminsNotifierPlugin].foreach{
					  _.sendAdminsNotification(Utils.baseUrl(request), "Dataset","added",dt.id.stringify, dt.name)}
				  Redirect(routes.Datasets.dataset(dt.id)) 
	            }	            
	          }  
	        }
		  }
		 )
        }
        case None => Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new datasets.")
      }
  }

  def metadataSearch() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.metadataSearch())
  }

  def generalMetadataSearch() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.generalMetadataSearch())
  }
}


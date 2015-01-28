package controllers

import api.WithPermission
import api.Permission
import models.{UUID, VersusIndexTypeName}
import play.api.libs.json.{Json, JsValue}
import services.{SectionIndexInfoService, AppConfiguration, VersusPlugin}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

import play.api.Logger

import scala.concurrent._
import javax.inject.{Inject, Singleton}

import play.api.data.Form
import play.api.data.Forms._
import scala.concurrent.duration.Duration
/**
 * Administration pages.
 *
 * @author Luigi Marini
 *
 */
@Singleton
class Admin @Inject() (sectionIndexInfo: SectionIndexInfoService) extends SecuredController {

  def main = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    val theme = AppConfiguration.getTheme
    Logger.debug("Theme id " + theme)
    implicit val user = request.user
    Ok(views.html.admin(theme, AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage))
  }
  
  def adminIndex = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    implicit val user = request.user
    Ok(views.html.adminIndex())
  }

  def reindexFiles = SecuredAction(parse.json, authorization = WithPermission(Permission.AddIndex)) { request =>
    Ok("Reindexing")
  }

  def test = SecuredAction(parse.json, authorization = WithPermission(Permission.Public)) { request =>
    Ok("""{"message":"test"}""").as(JSON)
  }

  def secureTest = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }

  //get the available Adapters from Versus
  def getAdapters() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
        	//Change to Non-Blocking
            var adapterListResponse = Await.result(plugin.getAdapters(),Duration.Inf)  
//            var adapterListResponse = plugin.getAdapters()
//
//            for {
//              adapterList <- adapterListResponse
//            } yield {
//              Ok(adapterList.json)
//            }
            Ok(adapterListResponse.json)		
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
            Ok("No Versus Service")
          }
        } //match

//      } //Async


  }

  // Get available extractors from Versus
  def getExtractors() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
        	//Change to Non-Blocking
            var extractorListResponse = Await.result(plugin.getExtractors(),Duration.Inf)  
//            var extractorListResponse = plugin.getExtractors()
//
//            for {
//              extractorList <- extractorListResponse
//            } yield {
//              Ok(extractorList.json)
//            }
            //Ok(adapterListResponse)
            Ok(extractorListResponse.json)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
        	  Ok("No Versus Service")
          }
        } //match

//      } //Async

  }
  
  //Get available Measures from Versus 
  def getMeasures() = SecuredAction(authorization=WithPermission(Permission.Admin)){
     request =>
      
//    Async{  
    	current.plugin[VersusPlugin] match {
     
        case Some(plugin)=>{
			// Change from Blocking to Non-Blocking            
        	var measureListResponse= Await.result(plugin.getMeasures(),Duration.Inf)  
//        	var measureListResponse= plugin.getMeasures()
//        	 
//        	for{
//        	  measureList<-measureListResponse
//        	}yield{
//        	 Ok(measureList.json)
//        	}
        	 //Ok(adapterListResponse)
        	Ok(measureListResponse.json) 
            }//case some
         
		 case None=>{
//		      Future(Ok("No Versus Service"))
		    Ok("No Versus Service")
		       }     
		 } //match
    
//   } //Async
        
        
  }

  //Get available Indexers from Versus 
  def getIndexers() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
			// Change from Blocking to Non-Blocking  
            var indexerListResponse = Await.result(plugin.getIndexers(),Duration.Inf)
//            var indexerListResponse = plugin.getIndexers()
//
//            for {
//              indexerList <- indexerListResponse
//            } yield {
//              Ok(indexerList.json)
//            }
			Ok(indexerListResponse.json)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
        	Ok("No Versus Service")
          }
        } //match

//      } //Async


  }

  /**
   * Get adapter, extractor,measure and indexer value and send it to VersusPlugin to send a create index request to Versus
   * If an index has type and/or name, store them in mongo db.
   * 
   */ 
   def createIndex() = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) {
     implicit request =>
//       Async {
         current.plugin[VersusPlugin] match {
           case Some(plugin) => {
             Logger.trace("Contr.Admin.CreateIndex()")
             val adapter = (request.body \ "adapter").as[String]
             val extractor = (request.body \ "extractor").as[String]
             val measure = (request.body \ "measure").as[String]
             val indexer = (request.body \ "indexer").as[String]
             val indexType = (request.body \ "indexType").as[String]      
             val indexName = (request.body \ "name").as[String]
             //create index and get its id
              val indexIdFuture :Future[models.UUID] = plugin.createIndex(adapter, extractor, measure, indexer)            
              //save index type (census sections, face sections, etc) to the mongo db
             if (indexType != null && indexType.length !=0){
             	indexIdFuture.map(sectionIndexInfo.insertType(_, indexType))          
             }             
             //save index name to the mongo db
             if (indexName != null && indexName.length !=0){
             	indexIdFuture.map(sectionIndexInfo.insertName(_, indexName))
             }           
//              Future(Ok("Index created successfully"))  
             Ok("Index created successfully")
           } //end of case some plugin

           case None => {
//             Future(Ok("No Versus Service"))
             Ok("No Versus Service")
           }
         } //match

//       } //Async
   }
   
  /**
   * Gets indexes from Versus, using VersusPlugin. Checks in mongo on Medici side if these indexes
   * have type and/or name. Adds type and/or name to json object and calls view template to display.
   */
  def getIndexes() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>
//      Async {        
        current.plugin[VersusPlugin] match {
          case Some(plugin) => {
           Logger.trace(" Admin.getIndexes()")
//            var indexListResponse = plugin.getIndexes()
           var indexListResponse = Await.result(plugin.getIndexes(),Duration.Inf) 
//            for {
//              indexList <- indexListResponse
//            } yield {
            	if(indexListResponse.body.isEmpty())
            	{ 
            		Ok(Json.toJson(""))
            	}
                else{
                  var finalJson :JsValue=null
                  val jsArray = indexListResponse.json
                  //make sure we got correctly formatted list of values
                  jsArray.validate[List[VersusIndexTypeName]].fold(
                		  // Handle the case for invalid incoming JSON.
                		  // Note: JSON created in Versus IndexResource.listJson must have the same names as Medici models.VersusIndexTypeName 
                		  error => {
                		    Logger.error("Admin.getIndexes - validation error")
                		    InternalServerError("Received invalid JSON response from remote service.")
                		    },
                		 
                		    // Handle a deserialized array of List[VersusIndexTypeName]
                		    indexes => {
                		    	Logger.debug("Admin.getIndexes indexes received = " + indexes)   								  
                		    	val indexesWithNameType = indexes.map{
                		    		index=>
                		    		  	//check in mongo for name/type of each index
                		    			val indType = sectionIndexInfo.getType(UUID(index.indexID)).getOrElse("")
                		    			val indName = sectionIndexInfo.getName(UUID(index.indexID)).getOrElse("")

                		    			//add type/name to index
                		    			VersusIndexTypeName.addTypeAndName(index, indType, indName)
   								  }                		    
                		    	indexesWithNameType.map(i=> Logger.debug("Admin.getIndexes index with name = " + i))
                		    
                		    	// Serialize as JSON, requires the implicit `format` defined earlier in VersusIndexTypeName
                		    	finalJson = Json.toJson(indexesWithNameType)    			
                		    }
                		  ) //end of fold                
                		  Ok(finalJson)
                	}
//            }
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
            Ok("No Versus Service")
          }
        } //match
//      } //Async
  }
 

  //build a specific index in Versus
  def buildIndex(id: String) = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>
           Logger.trace("Inside Admin.buildIndex(), index = " + id)
//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
			// Change from Blocking to Non-Blocking  
            var buildResponse = Await.result(plugin.buildIndex(UUID(id)),Duration.Inf) 
//            var buildResponse = plugin.buildIndex(id)
//
//            for {
//              buildRes <- buildResponse
//            } yield {
//              Ok(buildRes.body)
//            }
			Ok(buildResponse.body)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
			Ok("No Versus Service")
          }
        } //match

//      }

  }
  
  //Delete a specific index in Versus
  def deleteIndex(id: String)=SecuredAction(authorization=WithPermission(Permission.Admin)){
    request =>
//    Async{  
      current.plugin[VersusPlugin] match {
     
        case Some(plugin)=>{
			// Change from Blocking to Non-Blocking  
            var deleteIndexResponse = Await.result(plugin.deleteIndex(UUID(id)),Duration.Inf)         	 
//        	var deleteIndexResponse= plugin.deleteIndex(id)
//        	 
//        	for{
//        	  deleteIndexRes<-deleteIndexResponse
//        	}yield{
//        	 Ok(deleteIndexRes.body)
//        	}
        	Ok(deleteIndexResponse.body)
        	         
            }//case some
         
		 case None=>{
//		      Future(Ok("No Versus Service"))
			Ok("No Versus Service")		   
		       }     
		 } //match
    
//    } 
   
  }

  //Delete all indexes in Versus

  def deleteAllIndexes() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {
        	
          case Some(plugin) => {
			// Change from Blocking to Non-Blocking  
			var deleteAllResponse = Await.result(plugin.deleteAllIndexes,Duration.Inf) 
//            var deleteAllResponse = plugin.deleteAllIndexes()
//
//            for {
//              deleteAllRes <- deleteAllResponse
//            } yield {
//              Ok(deleteAllRes.body)
//            }
			Ok(deleteAllResponse.body)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
			Ok("No Versus Service")            
          }
        } //match

//      }
  }
  
  def setTheme() = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { implicit request =>
    request.body.\("theme").asOpt[String] match {
      case Some(theme) => {
        AppConfiguration.setTheme(theme)
        Ok("""{"status":"ok"}""").as(JSON)
      }
      case None => {
        Logger.error("no theme specified")
        BadRequest
      }
    }
  }

  val adminForm = Form(
  single(
    "email" -> email
  )verifying("Admin already exists.", fields => fields match {
     		case adminMail => !AppConfiguration.checkAdmin(adminMail)
     	})
)
  
  def newAdmin()  = SecuredAction(authorization=WithPermission(Permission.UserAdmin)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newAdmin(adminForm))
  }
  
  def submitNew() = SecuredAction(authorization=WithPermission(Permission.UserAdmin)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        if (x.email.nonEmpty && AppConfiguration.checkAdmin(x.email.get)) {
          adminForm.bindFromRequest.fold(
            errors => BadRequest(views.html.newAdmin(errors)),
            newAdmin => {
              AppConfiguration.addAdmin(newAdmin)
              Redirect(routes.Admin.listAdmins())
            }
          )
        } else {
          Unauthorized("Not authorized")
        }
      }
    }
  }
  
  def listAdmins() = SecuredAction(authorization=WithPermission(Permission.UserAdmin)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        if (x.email.nonEmpty && AppConfiguration.checkAdmin(x.email.get)) {
          val admins = AppConfiguration.getAdmins
          Ok(views.html.listAdmins(admins))
        } else {
          Unauthorized("Not authorized")
        }
      }
    }
  }

}

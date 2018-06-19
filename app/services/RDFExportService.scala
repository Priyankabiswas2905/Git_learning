package services

import play.api.{Application, Configuration, Logger}
import play.api.Play.current
import org.bson.types.ObjectId
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.FileWriter

import Transformation.LidoToCidocConvertion
import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import models.Dataset
import models.UUID
import play.libs.Akka

import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import models.UUID
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

trait RDFExportService {
	def getRDFUserMetadataFile(id: String, mappingNumber: String="1"): Option[java.io.File]
	def getRDFURLsForFile(id: String): Option[JsValue]
	def getRDFURLsForDataset(id: String): Option[JsValue]
	def getRDFUserMetadataDataset(id: String, mappingNumber: String="1"): Option[java.io.File]
}

/**
 * Service allowing exporting of community-generated and XML-uploaded metadata of files and datasets as RDF.
 *
 *
 */
class RDFExportServiceImpl @Inject() (lifecycle: ApplicationLifecycle,
	files: FileService,
	datasets: DatasetService,
	previews: PreviewService,
	actorSystem: ActorSystem,
	configuration: Configuration) extends RDFExportService {

  val resultsDir = System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "clowder__rdfdumptemporaryfiles" + System.getProperty("file.separator")
  val filesMappingsQuantity = Integer.parseInt(configuration.get[String]("filesxmltordfmapping.dircount"))
  val datasetsMappingsQuantity = Integer.parseInt(configuration.get[String]("datasetsxmltordfmapping.dircount"))
  
  val hostUrl = "http://" + configuration.get[String]("hostIp").replaceAll("/$", "") + ":" + configuration.get[String]("http.port")


	Logger.debug("Starting RDF exporter Plugin")
	//Clean temporary RDF files
	var timeInterval = configuration.get[Int]("rdfTempCleanup.checkEvery")
	actorSystem.scheduler.schedule(0.minutes, timeInterval.intValue().minutes){
		files.removeTemporaries()
	}

	lifecycle.addStopHook { () =>
		Logger.debug("Shutting down RDF exporter Plugin")
		Future.successful(())
	}

  lazy val enabled = {
		import play.api.Play.current
    !current.configuration.getString("rdfexportservice").filter(_ == "disabled").isDefined
  }
  
  def getRDFUserMetadataFile(id: String, mappingNumber: String="1"): Option[java.io.File] = {
    
    val uuId = UUID(id)    
    files.get(uuId) match { 
	            case Some(file) => {
	              val theJSON = files.getUserMetadataJSON(uuId)
	              val fileSep = System.getProperty("file.separator")
	              
	              //for Unix we need an extra \ in the directory path of the LidoToCidocConvertion output file due to Windows-based behavior of LidoToCidocConvertion  
	              var extraChar = ""
	              val OS = System.getProperty("os.name").toLowerCase()
	              if(OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") >= 0)
	                extraChar = "\\"
	              
		          var resultDir = resultsDir + new ObjectId().toString
		          new java.io.File(resultDir).mkdirs()
	              
	              if(!theJSON.replaceAll(" ","").equals("{}")){
		              val xmlFile = jsonToXML(theJSON)
		              new LidoToCidocConvertion(configuration.get[String]("filesxmltordfmapping.dir_"+mappingNumber), xmlFile.getAbsolutePath(), resultDir)
		              xmlFile.delete()
	              }
	              else{
	                new java.io.File(resultDir + fileSep + extraChar + "Results.rdf").createNewFile()
	              }
	              val resultFile = new java.io.File(resultDir + fileSep + extraChar + "Results.rdf")
	              Some(resultFile)	              
	            }
	            case None => None
	    }    
  }
  
  def getRDFURLsForFile(id: String): Option[JsValue] = {
    val uuId = UUID(id)   
    files.get(uuId)  match {
	      case Some(file) => {
	        
	        //RDF from XML of the file itself (for XML metadata-only files)
	        val previewsList = previews.findByFileId(uuId)
	        var rdfPreviewList = List.empty[models.Preview]
	        for(currPreview <- previewsList){
	          if(currPreview.contentType.equals("application/rdf+xml")){
	            rdfPreviewList = rdfPreviewList :+ currPreview
	          }
	        }        
	        var list = for (currPreview <- rdfPreviewList) yield Json.toJson(hostUrl + api.routes.Previews.download(currPreview.id))
	        
	        //RDF from export of file community-generated metadata to RDF 
	        for(i <- 1 to filesMappingsQuantity){
	          var currHostString = hostUrl + api.routes.Files.getRDFUserMetadata(uuId,i.toString)
	          list = list :+ Json.toJson(currHostString)
	        }
	
	        val listJson = Json.toJson(list.toList)
	        Some(listJson)
	      }
	      case None => None
	    }
  }
  
  def getRDFUserMetadataDataset(id: String, mappingNumber: String="1"): Option[java.io.File] = {
    val uuId = UUID(id) 
    datasets.get(uuId) match { 
	            case Some(dataset) => {
	              val theJSON = datasets.getUserMetadataJSON(uuId)
	              val fileSep = System.getProperty("file.separator")
	              
	              //for Unix we need an extra \ in the directory path of the LidoToCidocConvertion output file due to Windows-based behavior of LidoToCidocConvertion  
	              var extraChar = ""
	              val OS = System.getProperty("os.name").toLowerCase()
	              if(OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") >= 0)
	                extraChar = "\\"
	              
		          var resultDir = resultsDir + new ObjectId().toString
		          new java.io.File(resultDir).mkdirs()
	              
	              if(!theJSON.replaceAll(" ","").equals("{}")){
		              val xmlFile = jsonToXML(theJSON)
		              new LidoToCidocConvertion(configuration.get[String]("datasetsxmltordfmapping.dir_"+mappingNumber), xmlFile.getAbsolutePath(), resultDir)
		              xmlFile.delete()
	              }
	              else{
	                new java.io.File(resultDir + fileSep + extraChar + "Results.rdf").createNewFile()
	              }
	              val resultFile = new java.io.File(resultDir + fileSep + extraChar + "Results.rdf")
	              Some(resultFile)	              
	            }
	            case None => None
	    }    
  }
  
  def getRDFURLsForDataset(id: String): Option[JsValue] = {
    val uuId = UUID(id) 
    datasets.get(uuId)  match {
	      case Some(dataset) => {
	        
	        //RDF from XML files in the dataset itself (for XML metadata-only files)
	        val previewsList = previews.findByDatasetId(uuId)
	        var rdfPreviewList = List.empty[models.Preview]
	        for(currPreview <- previewsList){
	          if(currPreview.contentType.equals("application/rdf+xml")){
	            rdfPreviewList = rdfPreviewList :+ currPreview
	          }
	        }        
	        var list = for (currPreview <- rdfPreviewList) yield Json.toJson(hostUrl + api.routes.Previews.download(currPreview.id))
	        
	        for(file <- dataset.files){
	           val filePreviewsList = previews.findByFileId(file)
	           var fileRdfPreviewList = List.empty[models.Preview]
	           for(currPreview <- filePreviewsList){
		           if(currPreview.contentType.equals("application/rdf+xml")){
		        	   fileRdfPreviewList = fileRdfPreviewList :+ currPreview
		           }
	           }
	           val filesList = for (currPreview <- fileRdfPreviewList) yield Json.toJson(hostUrl + api.routes.Previews.download(currPreview.id))
	           list = list ++ filesList
	        }
	        
	        //RDF from export of file community-generated metadata to RDF 
	        for(i <- 1 to filesMappingsQuantity){
	          var currHostString = hostUrl + api.routes.Datasets.getRDFUserMetadata(uuId,i.toString)
	          list = list :+ Json.toJson(currHostString)
	        }
	
	        val listJson = Json.toJson(list.toList)
	        Some(listJson)
	      }
	      case None => None
	    }
  }
  
  def jsonToXML(theJSON: String): java.io.File = {
    
    val jsonObject = new JSONObject(theJSON)    
    var xml = org.json.XML.toString(jsonObject)
    
    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while(currStart != -1){
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1,currStart)
      currEnd = xml.indexOf(">", currStart+1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart,currEnd+1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd+1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1)
    
    val xmlFile = java.io.File.createTempFile("xml",".xml")
    val fileWriter =  new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()
    
    return xmlFile    
  }
  
  
}
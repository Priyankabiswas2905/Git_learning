package services

import java.io._
import java.util.ArrayList

import Transformation.LidoToCidocConvertion
import akka.actor.ActorSystem
import javax.inject.Inject
import models._
import org.apache.commons.io.FileUtils
import org.bson.types.ObjectId
import org.json.JSONObject
import play.api.{Configuration, Logger}
import play.api.Play.current
import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.Execution.Implicits._
import play.libs.Akka

import scala.concurrent.Future
import scala.concurrent.duration._

trait RDFUpdateService

/**
 * External RDF store update service.
 *
 *
 */
class RDFUpdateServiceImpl @Inject() (lifecycle: ApplicationLifecycle, actorSystem: ActorSystem,
	configuration: Configuration) extends RDFUpdateService {

  val files: FileService = DI.injector.instanceOf[FileService]
  val datasets: DatasetService = DI.injector.instanceOf[DatasetService]
  val sparql: RdfSPARQLService = DI.injector.instanceOf[RdfSPARQLService]

  object MustBreak extends Exception {}

	Logger.debug("Starting RDF updater Plugin")

	var timeInterval = configuration.get[Int]("rdfRepoUpdate.updateEvery")
	actorSystem.scheduler.schedule(0.hours, timeInterval.intValue().hours) {
		modifyRDFOfMetadataChangedFiles()
		modifyRDFOfMetadataChangedDatasets()
	}

	lifecycle.addStopHook { () =>
		Logger.debug("Shutting down RDF updater Plugin")
		Future.successful(())
	}

  lazy val enabled = {
		import play.api.Play.current
    !current.configuration.getString("rdfupdateservice").filter(_ == "disabled").isDefined
  }


  def modifyRDFOfMetadataChangedFiles() {
    val changedFiles = files.findMetadataChangedFiles()
    for (changedFile <- changedFiles) {
      modifyRDFUserMetadataFiles(changedFile.id)
    }
  }

  def modifyRDFOfMetadataChangedDatasets() {
    val changedDatasets = datasets.findMetadataChangedDatasets()
    for (changedDataset <- changedDatasets) {
      modifyRDFUserMetadataDatasets(changedDataset.id)
    }
  }

 def modifyRDFUserMetadataFiles(id: UUID, mappingNumber: String="1") = {
    sparql.removeFileFromGraphs(id, "rdfCommunityGraphName")
    files.get(id) match { 
	            case Some(file) => {
	              val theJSON = files.getUserMetadataJSON(id)
	              val fileSep = System.getProperty("file.separator")
	              
	              //for Unix we need an extra \ in the directory path of the LidoToCidocConvertion output file due to Windows-based behavior of LidoToCidocConvertion  
	              var extraChar = ""
	              val OS = System.getProperty("os.name").toLowerCase()
	              if(OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") >= 0)
	                extraChar = "\\"
	              
	              val tmpDir = System.getProperty("java.io.tmpdir")
		          var resultDir = tmpDir + fileSep + "clowder__rdfuploadtemporaryfiles" + fileSep + new ObjectId().toString
		          val resultDirFile = new java.io.File(resultDir)
		          resultDirFile.mkdirs()
	              
	              if(!theJSON.replaceAll(" ","").equals("{}")){
		              val xmlFile = jsonToXML(theJSON)
		              new LidoToCidocConvertion(configuration.get[String]("filesxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)
		              xmlFile.delete()
	              }
	              else{
	                new java.io.File(resultDir + fileSep + extraChar + "Results.rdf").createNewFile()
	              }
	              val resultFile = new java.io.File(resultDir + fileSep + extraChar + "Results.rdf")
	              
	              //Connecting RDF metadata with the entity describing the original file
					val rootNodes = new ArrayList[String]()
					val rootNodesFile = configuration.get[String]("rootNodesFile")
					Logger.debug(rootNodesFile)
					if(!rootNodesFile.equals("*")){
						val rootNodesReader = new BufferedReader(new FileReader(new java.io.File(rootNodesFile)))						
						var line = rootNodesReader.readLine()  
						while (line != null){
						    Logger.debug((line == null).toString() ) 
							rootNodes.add(line.trim())
							line = rootNodesReader.readLine() 
						}
						rootNodesReader.close()
					}
					
					val resultFileConnected = java.io.File.createTempFile("ResultsConnected", ".rdf")
					
					val fileWriter =  new BufferedWriter(new FileWriter(resultFileConnected))		
					val fis = new FileInputStream(resultFile)
					val data = new Array[Byte]  (resultFile.length().asInstanceOf[Int])
				    fis.read(data)
				    fis.close()
				    resultFile.delete()
				    FileUtils.deleteDirectory(resultDirFile)
				    //
				    val s = new String(data, "UTF-8")
					val rdfDescriptions = s.split("<rdf:Description")
					fileWriter.write(rdfDescriptions(0))
					var i = 0
					for( i <- 1 to (rdfDescriptions.length - 1)){
						fileWriter.write("<rdf:Description" + rdfDescriptions(i))
						if(rdfDescriptions(i).contains("<rdf:type")){
							var isInRootNodes = false
							if(rootNodesFile.equals("*"))
								isInRootNodes = true
							else{
								var j = 0
								try{
									for(j <- 0 to (rootNodes.size()-1)){
										if(rdfDescriptions(i).contains("\"" + rootNodes.get(j) + "\"")){
											isInRootNodes = true
											throw MustBreak
										}
									}
								}catch {case MustBreak => }
							}
							
							if(isInRootNodes){
								val theResource = rdfDescriptions(i).substring(rdfDescriptions(i).indexOf("\"")+1, rdfDescriptions(i).indexOf("\"", rdfDescriptions(i).indexOf("\"")+1))
								val theHost = "http://" + configuration.get[String]("hostIp").replaceAll("/$", "") + ":" + configuration.get[String]("http.port")
								var connection = "<rdf:Description rdf:about=\"" + theHost +"/api/files/"+ id
								connection = connection	+ "\"><P129_is_about xmlns=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\" rdf:resource=\"" + theResource
								connection = connection	+ "\"/></rdf:Description>"
								fileWriter.write(connection)
							}	
						}
					}
					fileWriter.close()
	              
					sparql.addFromFile(id, resultFileConnected, "file") 
					resultFileConnected.delete()
					
					sparql.addFileToGraph(id, "rdfCommunityGraphName") 
					
					files.setUserMetadataWasModified(id, false) 
	            }
	            case None => {}
	 }
  }


	def modifyRDFUserMetadataDatasets(id: UUID, mappingNumber: String="1") = {
	 	sparql.removeDatasetFromUserGraphs(id)
	 	datasets.get(id) match { 
	            case Some(dataset) => {
	              val theJSON = datasets.getUserMetadataJSON(id)
	              val fileSep = System.getProperty("file.separator")
	              
	              //for Unix we need an extra \ in the directory path of the LidoToCidocConvertion output file due to Windows-based behavior of LidoToCidocConvertion  
	              var extraChar = ""
	              val OS = System.getProperty("os.name").toLowerCase()
	              if(OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") >= 0)
	                extraChar = "\\"
	              
	              val tmpDir = System.getProperty("java.io.tmpdir")
		          var resultDir = tmpDir + fileSep + "clowder__rdfuploadtemporaryfiles" + fileSep + new ObjectId().toString
		          val resultDirFile = new java.io.File(resultDir)
		          resultDirFile.mkdirs()
	              
	              if(!theJSON.replaceAll(" ","").equals("{}")){
		              val xmlFile = jsonToXML(theJSON)
		              new LidoToCidocConvertion(configuration.get[String]("datasetsxmltordfmapping.dir_"+mappingNumber), xmlFile.getAbsolutePath(), resultDir)
		              xmlFile.delete()
	              }
	              else{
	                new java.io.File(resultDir + fileSep + extraChar + "Results.rdf").createNewFile()
	              }
	              val resultFile = new java.io.File(resultDir + fileSep + extraChar + "Results.rdf")
	              
	              //Connecting RDF metadata with the entity describing the original file
					val rootNodes = new ArrayList[String]()
					val rootNodesFile = configuration.getString("datasetRootNodesFile").getOrElse("")
					Logger.debug(rootNodesFile)
					if(!rootNodesFile.equals("*")){
						val rootNodesReader = new BufferedReader(new FileReader(new java.io.File(rootNodesFile)))						
						var line = rootNodesReader.readLine()  
						while (line != null){
						    Logger.debug((line == null).toString() ) 
							rootNodes.add(line.trim())
							line = rootNodesReader.readLine() 
						}
						rootNodesReader.close()
					}
					
					val resultFileConnected = java.io.File.createTempFile("ResultsConnected", ".rdf")
					
					val fileWriter =  new BufferedWriter(new FileWriter(resultFileConnected))		
					val fis = new FileInputStream(resultFile)
					val data = new Array[Byte]  (resultFile.length().asInstanceOf[Int])
				    fis.read(data)
				    fis.close()
				    resultFile.delete()
				    FileUtils.deleteDirectory(resultDirFile)
				    //
				    val s = new String(data, "UTF-8")
					val rdfDescriptions = s.split("<rdf:Description")
					fileWriter.write(rdfDescriptions(0))
					var i = 0
					for( i <- 1 to (rdfDescriptions.length - 1)){
						fileWriter.write("<rdf:Description" + rdfDescriptions(i))
						if(rdfDescriptions(i).contains("<rdf:type")){
							var isInRootNodes = false
							if(rootNodesFile.equals("*"))
								isInRootNodes = true
							else{
								var j = 0
								try{
									for(j <- 0 to (rootNodes.size()-1)){
										if(rdfDescriptions(i).contains("\"" + rootNodes.get(j) + "\"")){
											isInRootNodes = true
											throw MustBreak
										}
									}
								}catch {case MustBreak => }
							}
							
							if(isInRootNodes){
								val theResource = rdfDescriptions(i).substring(rdfDescriptions(i).indexOf("\"")+1, rdfDescriptions(i).indexOf("\"", rdfDescriptions(i).indexOf("\"")+1))
								val theHost = "http://" + configuration.get[String]("hostIp").replaceAll("/$", "") + ":" + configuration.get[String]("http.port")
								var connection = "<rdf:Description rdf:about=\"" + theHost +"/api/datasets/"+ id
								connection = connection	+ "\"><P129_is_about xmlns=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\" rdf:resource=\"" + theResource
								connection = connection	+ "\"/></rdf:Description>"
								fileWriter.write(connection)
							}	
						}
					}
					fileWriter.close()
	              
					sparql.addFromFile(id, resultFileConnected, "dataset")
			        resultFileConnected.delete()
			
			        sparql.addDatasetToGraph(id, "rdfCommunityGraphName")
			
			        datasets.setUserMetadataWasModified(id, false)
	            }
	            case None => {}
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

}


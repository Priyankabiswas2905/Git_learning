package services

import play.api.{ Plugin, Logger, Application }
import java.util.Date
import play.api.libs.json.Json
import play.api.Play.current
import play.api.libs.ws.WS
import play.api.libs.concurrent.Promise
import java.io._
import models.FileMD
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import services._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Input.{ El, EOF, Empty }
import com.mongodb.casbah.gridfs.GridFS
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import java.util.Date
import models.File
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.json.Json._
import scala.concurrent.{ future, blocking, Future, Await }
import scala.concurrent.ExecutionContext.Implicits.global
import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import models.PreviewDAO
import controllers.Previewers
import controllers.routes
import java.text.DecimalFormat

import scala.collection.mutable.ArrayBuffer

/*Versus Plugin
 * 
 * @author Smruti
 * 
 * */

class VersusPlugin(application: Application) extends Plugin {

  override def onStart() {
    Logger.debug("Starting Versus Plugin")

  }
  def getAdapters():scala.concurrent.Future[play.api.libs.ws.Response]={
    val configuration = play.api.Play.configuration
    val host=configuration.getString("versus.host").getOrElse("")
    val adapterUrl=host+"/adapters"
    val adapterList:scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(adapterUrl).withHeaders("Accept"->"application/json").get()
    adapterList.map{
      response=>Logger.debug("GET: AdapterLister: response.body="+response.body)
    }
       adapterList
  }
  def getExtractors():scala.concurrent.Future[play.api.libs.ws.Response]={
    val configuration = play.api.Play.configuration
    val host=configuration.getString("versus.host").getOrElse("")
    val extractorUrl=host+"/extractors"
    val extractorList:scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(extractorUrl).withHeaders("Accept"->"application/json").get()
    extractorList.map{
      response=>Logger.debug("GET: ExtractorList: response.body="+response.body)
    }
       extractorList
  }
  def getMeasures():scala.concurrent.Future[play.api.libs.ws.Response]={
    val configuration = play.api.Play.configuration
    val host=configuration.getString("versus.host").getOrElse("")
    val measureUrl=host+"/measures"
    val measureList:scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(measureUrl).withHeaders("Accept"->"application/json").get()
    measureList.map{
      response=>Logger.debug("GET: measureList: response.body="+response.body)
    }
       measureList
  }
  def getIndexers():scala.concurrent.Future[play.api.libs.ws.Response]={
    val configuration = play.api.Play.configuration
    val host=configuration.getString("versus.host").getOrElse("")
    val indexerUrl=host+"/indexers"
    val indexerList:scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(indexerUrl).withHeaders("Accept"->"application/json").get()
    indexerList.map{
      response=>Logger.debug("GET: indexerList: response.body="+response.body)
    }
       indexerList
  }

  // Get all indexes from Versus
  def getIndexes(): scala.concurrent.Future[play.api.libs.ws.Response] = {

    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/index"
    var k = 0
    val indexList: scala.concurrent.Future[play.api.libs.ws.Response] = WS.url(indexurl).withHeaders("Accept"->"application/json").get()
    indexList.map {
      response => Logger.debug("GETINDEXES: response.body=" + response.body)
    }
    indexList
  }
  def deleteIndex(indexId: String): scala.concurrent.Future[play.api.libs.ws.Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val deleteurl = host + "/index/" + indexId 
    Logger.debug("IndexID=" + indexId);
    var deleteResponse: scala.concurrent.Future[play.api.libs.ws.Response] = WS.url(deleteurl).delete()
    deleteResponse.map {
      r => Logger.debug("r.body" + r.body);

    }
    deleteResponse
  }

  def deleteAllIndexes(): scala.concurrent.Future[play.api.libs.ws.Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/index"

    val response: scala.concurrent.Future[play.api.libs.ws.Response] = WS.url(indexurl).delete()
    response
  }

  def createIndex(adapter: String, extractor: String, measure: String, indexer: String): scala.concurrent.Future[play.api.libs.ws.Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")

    val createIndexUrl = host + "/index";
    Logger.debug("Form Parameters: " + adapter + " " + extractor + " " + measure + " " + indexer);
    Logger.debug("theurl: "+createIndexUrl);
    val response = WS.url(createIndexUrl).post(Map("Adapter" -> Seq(adapter), "Extractor" -> Seq(extractor), "Measure" -> Seq(measure), "Indexer" -> Seq(indexer))).map {
      res =>
        //Logger.debug("res.body="+res.body)
        res
    }
    response
  }
  
  
  //index your file
  def index(id: String, fileType: String) {

    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId = configuration.getString("versus.index").getOrElse("")
    
    val urlf = client + "/api/files/" + id + "?key=" + configuration.getString("commKey").get
    val host = configuration.getString("versus.host").getOrElse("")

    var indexurl = host + "/index/" + indexId + "/add"

    val indexList = getIndexes()
    var k = 0
    indexList.map {
      response =>
        Logger.debug("response.body=" + response.body)
        val json: JsValue = Json.parse(response.body)
        Logger.debug("index(): json=" + json);
        val list = json.as[Seq[models.IndexList.IndexList]]
        val len = list.length
        val indexes = new Array[(String, String)](len)

        list.map {
          index =>
            Logger.debug("indexID=" + index.indexID + " MIMEType=" + index.MIMEtype+" fileType="+fileType);
            indexes.update(k, (index.indexID, index.MIMEtype))
            val typeA=fileType.split("/")
            val typeB=index.MIMEtype.split("/")
           // if (fileType.equals(index.MIMEtype)) {
            if (typeA(0).equals(typeB(0))) {
              indexurl = host + "/index/" + index.indexID + "/add"
              WS.url(indexurl).post(Map("infile" -> Seq(urlf))).map {
                res =>
                  Logger.debug("res.body=" + res.body)

              } //WS map end
            } //if fileType end
        }

    }

  }
  def buildIndex(indexId:String):scala.concurrent.Future[play.api.libs.ws.Response]={
    val configuration = play.api.Play.configuration
    //val indexId=configuration.getString("versus.index").getOrElse("")
    val host=configuration.getString("versus.host").getOrElse("")
    val buildurl=host+"/index/"+indexId+"/build"
        Logger.debug("IndexID="+indexId);
    var buildResponse:scala.concurrent.Future[play.api.libs.ws.Response]=WS.url(buildurl).post("")
    buildResponse.map{
    	r=>Logger.debug("r.body"+r.body);
    	
     }
    buildResponse
  }

  

  //  def query(id:String):scala.concurrent.Future[play.api.libs.ws.Response]= {

 /* def query(id: String): scala.concurrent.Future[Array[(String, String, Double, String)]] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId = configuration.getString("versus.index").getOrElse("")
    //val query= client+"/queries/"+id+"/blob"
    val query = client + "/api/queries/" + id + "?key=letmein"
    val host = configuration.getString("versus.host").getOrElse("")

    val queryurl = host + "/index/" + indexId + "/query"

    val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response] = WS.url(queryurl).post(Map("infile" -> Seq(query)))

    resultFuture.map {
      res =>
        val json: JsValue = Json.parse(res.body)
        val simvalue = json.as[Seq[models.Result.Result]]

        val l = simvalue.length
        val ar = new Array[String](l)
        val se = new Array[(String, String, Double, String)](l)
        var i = 0
        simvalue.map {
          s =>
            val a = s.docID.split("/")
            val n = a.length - 2
            Services.files.getFile(a(n)) match {
              case Some(file) => {
                se.update(i, (a(n), s.docID, s.proximity, file.filename))
                ar.update(i, file.filename)
                Logger.debug("i" + i + " name=" + ar(i) + "se(i)" + se(i)._3)
                i = i + 1
              }
              case None => None

            }

        }
        se
    }

  }
*/
 

 
 def queryIndex(id: String, indxId: String): scala.concurrent.Future[(String, scala.collection.mutable.ArrayBuffer[(String, String, Double, String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])])] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
   
    val indexId = indxId
   
    val query = client + "/api/queries/" + id + "?key=" + configuration.getString("commKey").get
    val host = configuration.getString("versus.host").getOrElse("")

    var queryurl = host + "/index/" + indexId + "/query"

    val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response] = WS.url(queryurl).post(Map("infile" -> Seq(query)))

    resultFuture.map {
      response =>
        val json: JsValue = Json.parse(response.body)
        val similarity_value = json.as[Seq[models.Result.Result]]

        val len = similarity_value.length
       // val ar = new Array[String](len)
        val se = new Array[(String, String, Double, String)](len)

        //var resultArray = new ArrayBuffer[(String, String, Double, String)]()
        var resultArray = new ArrayBuffer[(String, String, Double, String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])]()
        var i = 0
        similarity_value.map {
          result =>
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");
            val subStr = result.docID.substring(begin + 1, end);
        //    val a = result.docID.split("/")
          //  val n = a.length - 2
            Services.files.getFile(subStr) match {

              case Some(file) => {
                // se.update(i,(a(n),result.docID,result.proximity,file.filename))
                //Previews..............
                val previewsFromDB = PreviewDAO.findByFileId(file.id)
                val previewers = Previewers.findPreviewers
                //Logger.info("Number of previews " + previews.length);
                val files = List(file)
                val previewslist = for (f <- files) yield {
                  val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
                    (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
                  }
                  if (pvf.length > 0) {
                    (file -> pvf)
                  } else {
                    val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
                      (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id.toString) + "/blob", file.contentType, file.length)
                    }
                    (file -> ff)
                  }
                }
                val previews = Map(previewslist: _*)
                ///Previews ends.........
                
                //resultArray += ((subStr, result.docID, result.proximity, file.filename))
                //resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                 val formatter = new DecimalFormat("#.###")
               // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val proxvalue=formatter.format(result.proximity).toDouble
                resultArray += ((subStr, result.docID, proxvalue, file.filename,previews))
             
                //     ar.update(i, file.filename)
                //Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
                Logger.debug("IndxId="+ indexId+" resultArray=(" + subStr + " , " + result.proximity + ", " + file.filename + ")\n")
                i = i + 1
              }
              case None => None

            }

        } // End of similarity map
        //se
        (indexId, resultArray)
    }
  }
  
  
  //Query index for a file from  the database 
  
  def queryIndexFile(id: String, indxId: String): scala.concurrent.Future[(String, scala.collection.mutable.ArrayBuffer[(String, String, Double, String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])])] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
   
    val indexId = indxId
   
    val query = client + "/api/files/" + id + "?key=" + configuration.getString("commKey").get
    val host = configuration.getString("versus.host").getOrElse("")

    var queryurl = host + "/index/" + indexId + "/query"

    val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response] = WS.url(queryurl).post(Map("infile" -> Seq(query)))

    resultFuture.map {
      response =>
        val json: JsValue = Json.parse(response.body)
        val similarity_value = json.as[Seq[models.Result.Result]]

        val len = similarity_value.length
       // val ar = new Array[String](len)
        val se = new Array[(String, String, Double, String)](len)

        //var resultArray = new ArrayBuffer[(String, String, Double, String)]()
        var resultArray = new ArrayBuffer[(String, String, Double, String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])]()
        var i = 0
        similarity_value.map {
          result =>
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");
            val subStr = result.docID.substring(begin + 1, end);
        //    val a = result.docID.split("/")
          //  val n = a.length - 2
            Services.files.getFile(subStr) match {

              case Some(file) => {
                // se.update(i,(a(n),result.docID,result.proximity,file.filename))
                //Previews..............
                val previewsFromDB = PreviewDAO.findByFileId(file.id)
                val previewers = Previewers.findPreviewers
                //Logger.info("Number of previews " + previews.length);
                val files = List(file)
                val previewslist = for (f <- files) yield {
                  val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
                    (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
                  }
                  if (pvf.length > 0) {
                    (file -> pvf)
                  } else {
                    val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
                      (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id.toString) + "/blob", file.contentType, file.length)
                    }
                    (file -> ff)
                  }
                }
                val previews = Map(previewslist: _*)
                ///Previews ends.........
                
                //resultArray += ((subStr, result.docID, result.proximity, file.filename))
                val formatter = new DecimalFormat("#.###")
               // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val proxvalue=formatter.format(result.proximity).toDouble
                resultArray += ((subStr, result.docID, proxvalue, file.filename,previews))
             
                //     ar.update(i, file.filename)
                //Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
                Logger.debug("IndxId="+ indexId+" resultArray=(" + subStr + " , " + result.proximity + ", " + file.filename + ")\n")
                i = i + 1
              }
              case None => None

            }

        } // End of similarity map
        //se
        (indexId, resultArray)
    }
  }
  
  
  
  def queryURL(url: String, indxId: String): scala.concurrent.Future[(String, scala.collection.mutable.ArrayBuffer[(String, String, Double, String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])])] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
   
    val indexId = indxId
   
   // val query = client + "/api/queries/" + indxId + "?key=letmein"
    val query=url
    val host = configuration.getString("versus.host").getOrElse("")

    var queryurl = host + "/index/" + indexId + "/query"

    val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response] = WS.url(queryurl).post(Map("infile" -> Seq(query)))

    resultFuture.map {
      response =>
        val json: JsValue = Json.parse(response.body)
        val similarity_value = json.as[Seq[models.Result.Result]]

        val len = similarity_value.length
       // val ar = new Array[String](len)
        val se = new Array[(String, String, Double, String)](len)

        //var resultArray = new ArrayBuffer[(String, String, Double, String)]()
        var resultArray = new ArrayBuffer[(String, String, Double, String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])]()
        var i = 0
        similarity_value.map {
          result =>
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");
            val subStr = result.docID.substring(begin + 1, end);
        //    val a = result.docID.split("/")
          //  val n = a.length - 2
            Services.files.getFile(subStr) match {

              case Some(file) => {
                // se.update(i,(a(n),result.docID,result.proximity,file.filename))
                //Previews..............
                val previewsFromDB = PreviewDAO.findByFileId(file.id)
                val previewers = Previewers.findPreviewers
                //Logger.info("Number of previews " + previews.length);
                val files = List(file)
                val previewslist = for (f <- files) yield {
                  val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
                    (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
                  }
                  if (pvf.length > 0) {
                    (file -> pvf)
                  } else {
                    val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
                      (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id.toString) + "/blob", file.contentType, file.length)
                    }
                    (file -> ff)
                  }
                }
                val previews = Map(previewslist: _*)
                ///Previews ends.........
                
                //resultArray += ((subStr, result.docID, result.proximity, file.filename))
                //resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                 val formatter = new DecimalFormat("#.###")
               // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val proxvalue=formatter.format(result.proximity).toDouble
                resultArray += ((subStr, result.docID, proxvalue, file.filename,previews))
             
                //     ar.update(i, file.filename)
                //Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
                Logger.debug("IndxId="+ indexId+" resultArray=(" + subStr + " , " + result.proximity + ", " + file.filename + ")\n")
                i = i + 1
              }
              case None => None

            }

        } // End of similarity map
        //se
        (indexId, resultArray)
    }
  }
   
  override def onStop() {
    Logger.debug("Shutting down Versus Plugin")
  }
}


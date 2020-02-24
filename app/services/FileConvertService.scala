package services

import java.io._

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws._
import play.api.mvc.MultipartFormData.FilePart
import play.api.{Configuration, Logger}
import play.libs.Akka

import scala.concurrent._
import scala.concurrent.duration._

trait PolyglotService {
  def checkForFileAndDownload(triesLeft: Int, url: String, outputStream: OutputStream): Future[Unit]
  def getConvertedFileURL(filename: String, inputStream: InputStream, outputFormat: String): Future[String]
  def getOutputFormats(inputType: String): Future[Option[List[String]]]
  def getOutputFormatsPolyglot(inputType: String): Future[Option[List[String]]]
}

/**
 * File Convert Service
 *
 */
class FileConvertService(application: Application) {

  val fileConvertUser: Option[String] = configuration.getString("fileconvert.username")
  val fileConvertPassword: Option[String] = configuration.getString("fileconvert.password")
  val fileConvertConvertURL: Option[String] = configuration.getString("fileconvert.convertURL")
  val fileConvertInputsURL: Option[String] = configuration.getString("fileconvert.inputsURL")

  //table to store output formats for each input format
  //store them in memory to avoind making a request to Polyglot each time
  val formatsTable = scala.collection.mutable.HashMap.empty[String, List[String]]

  //using code from https://www.playframework.com/documentation/2.2.x/ScalaWS
  //Processing large responses
  def fromStream(stream: OutputStream): Iteratee[Array[Byte], Unit] = Cont {
    case e @ Input.EOF =>
      Logger.debug("fromStream case EOF")
      stream.close()
      Done((), e)
    case Input.El(data) =>
      Logger.debug("fromStream case input.El, data = " + data)
      stream.write(data)
      fromStream(stream)
    case Input.Empty =>
      Logger.debug("fromStream case empty , so calling fromStream again")
      fromStream(stream)
  }

  /**
   * Keeps checking for the file on Polyglot server until the file is found or until too many tries.
   */
  def checkForFileAndDownload(triesLeft: Int, url: String, outputStream: OutputStream): Future[Unit] =
    {
	  //check that credentials are set in config file
      if ( !fileConvertUser.isDefined || !fileConvertPassword.isDefined) {
        throw new RuntimeException("Polyglot credentials not defined.")
      }
      
      if (triesLeft == 0) Future.failed(throw new RuntimeException("Converted file not found."))
      
      else  WS.url(url).withAuth(fileConvertUser.get,  fileConvertPassword.get, AuthScheme.BASIC).get flatMap { res =>
        if (res.status == 200) {
          //this is the callback, runs after file exists is TRUE
          Logger.debug("File exists on polyglot. Will download now.")
          //file exists on Polyglot, begin download using iteratee
          //following example in https://www.playframework.com/documentation/2.2.x/ScalaWS  Processing large responses           
          val result = WS.url(url)
            .withAuth(fileConvertUser.get, fileConvertPassword.get, AuthScheme.BASIC)
            .get { xx => fromStream(outputStream) }
           	.flatMap(_.run)
          //Returning result. When it is mapped in the controller, the successful future is AFTER file has been downloaded on the Clowder server.
          futureResponse
        } else {
          Logger.debug("Checking if file exists on Polyglot, status = " + res.status + " " + res.statusText + ", call again in 3 sec")
          akka.pattern.after(3 seconds, using = actorSystem.scheduler)(checkForFileAndDownload((triesLeft - 1), url, outputStream))
          Future.failed(throw new RuntimeException("Error connecting to Polyglot."))
        }
      }
  }

  /**
    * Uploads to Polyglot the file to be converted. Returns url of the converted file.
    */
  def getConvertedFileURL(filename: String, inputStream: java.io.InputStream, outputFormat: String): Future[String] = {
    //check that Polyglot credentials are defined
    if (!fileConvertConvertURL.isDefined || !fileConvertUser.isDefined || !fileConvertPassword.isDefined) {
      throw new RuntimeException("Polyglot credentials not defined.")
    }

    //post a multipart form data to Polyglot.
    //based on the code from https://github.com/playframework/playframework/issues/902
    //comment dated Dec 5, 2014                   
    // Build up the Multiparts - consists of just one file part               
    val filePart: FilePart = new FilePart(filename, new ByteArrayPartSource(filename, IOUtils.toByteArray(inputStream)))
    val parts = Array[Part](filePart)
    val reqEntity = new MultipartRequestEntity(parts, new FluentCaseInsensitiveStringsMap)
    val baos = new ByteArrayOutputStream
    reqEntity.writeRequest(baos)
    val bytes = baos.toByteArray
    val reqContentType = reqEntity.getContentType

    // Now just send the data to the WS API                
    val response = WS.url(fileConvertConvertURL.get + outputFormat)
      .withAuth(fileConvertUser.get, fileConvertPassword.get, AuthScheme.BASIC)
      .post(bytes)(Writeable.wBytes, ContentTypeOf(Some(reqContentType)))

    //get the url for the converted file on Polyglot  
    val fileURLFut = response.map {
      res =>
        //get the url for the converted file on Polyglot
        if (res.status != 200) {
          Logger.debug("Could not get url of converted file - status = " + res.status + "  " + res.statusText)
          throw new RuntimeException("Could not connect to Polyglot. " + res.statusText)
        }
        //TODO: Find an easier way to get rid of html markup
        //result is an html link
        //<a href=http://the_url_string>http://the_url_string</a>
        val fileURL = res.body.substring(res.body.indexOf("http"), res.body.indexOf(">"))
        Future.successful(fileURL)
    }
  }

  /**
    * Gets all output formats for the given input format.
    * If outputs are stored in memory, returns them. Otherwise, calls another method to fetch outputs from Polyglot.
    */
  def getOutputFormats(inputType: String): Future[Option[List[String]]] = {
    for ((k, v) <- formatsTable) Logger.debug("key: " + k)

    //check if outputs are stored in the table in memory, and that they are non-empty
    if ((formatsTable contains inputType) && (formatsTable(inputType).length > 0)) {
      Future(Some(formatsTable(inputType)))
    } else {
      //output formats are not found, make a request to Polyglot to get them
      getOutputFormatsPolyglot(inputType)
    }
  }

  /**
   * Goes to Polyglot and fetches all output formats for the input format given.
   */
  def getOutputFormatsPolyglot(inputType: String): Future[Option[List[String]]] = {   
    //proceed only if received all the config params
    if (fileConvertInputsURL.isDefined && fileConvertUser.isDefined && fileConvertPassword.isDefined) {
      //call polyglot server with authentication          
      WS.url(fileConvertInputsURL.get + inputType)
        .withAuth(fileConvertUser.get, fileConvertPassword.get, AuthScheme.BASIC)
        .get
        .map {
          case response =>
            //If reponse was successful, get a list of output formats. Otherwise return None.
            val outputFormats = {
              if (response.status == 200) {
                Logger.debug("success getting response from Polyglot")
                val formatsList = response.body.split("\n").toList
                //save list of output formats in the table
                formatsTable(inputType) = formatsList
                Some(formatsList)
              } else {
                Logger.debug("Problems getting output formats from Polyglot, response status = " + response.status + ", " + response.statusText)
                None
              }
            }
          }
          outputFormats
      }
  }
}
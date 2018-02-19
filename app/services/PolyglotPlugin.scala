package services

import play.libs.Akka
import play.api.{Application, Logger, Plugin}
import java.io._
import javax.inject.Inject

import play.api.Play.{configuration, current}
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws._

import scala.concurrent._
import scala.concurrent.duration._
import com.ning.http.client.AsyncHttpClient
import org.apache.commons.codec.binary.Base64.encodeBase64
import com.ning.http.client.multipart.FilePart
import play.api.inject.ApplicationLifecycle

trait PolyglotService {
  def checkForFileAndDownload(triesLeft: Int, url: String, outputStream: OutputStream): Future[Unit]
  def getConvertedFileURL(filename: String, inputStream: InputStream, outputFormat: String): Future[String]
  def getOutputFormats(inputType: String): Future[Option[List[String]]]
  def getOutputFormatsPolyglot(inputType: String): Future[Option[List[String]]]
}

/**
 * Polyglot Plugin
 *
 */
class PolyglotServiceImpl @Inject() (lifecycle: ApplicationLifecycle) extends PolyglotService {

  val polyglotUser: Option[String] = configuration.getString("polyglot.username")
  val polyglotPassword: Option[String] = configuration.getString("polyglot.password")
  val polyglotConvertURL: Option[String] = configuration.getString("polyglot.convertURL")
  val polyglotInputsURL: Option[String] = configuration.getString("polyglot.inputsURL")

  //table to store output formats for each input format
  //store them in memory to avoind making a request to Polyglot each time
  val formatsTable = scala.collection.mutable.HashMap.empty[String, List[String]]

  Logger.debug("Starting Polyglot Plugin")

  lifecycle.addStopHook { () =>
    Logger.debug("Stopping Polyglot Plugin")
    Future.successful(())
  }

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
      if ( !polyglotUser.isDefined || !polyglotPassword.isDefined) {
        throw new RuntimeException("Polyglot credentials not defined.")
      }
      
      if (triesLeft == 0) Future.failed(throw new RuntimeException("Converted file not found."))
      
      else  WS.url(url).withAuth(polyglotUser.get,  polyglotPassword.get, WSAuthScheme.BASIC).get flatMap { res =>
        if (res.status == 200) {
          //this is the callback, runs after file exists is TRUE
          Logger.debug("File exists on polyglot. Will download now.")
          //file exists on Polyglot, begin download using iteratee
          //following example in https://www.playframework.com/documentation/2.2.x/ScalaWS  Processing large responses           
          val result = WS.url(url)
            .withAuth(polyglotUser.get, polyglotPassword.get, WSAuthScheme.BASIC)
            .get { xx => fromStream(outputStream) }
           	.flatMap(_.run)
          //Returning result. When it is mapped in the controller, the successful future is AFTER file has been downloaded on the Clowder server.
          result
        } else {
          Logger.debug("Checking if file exists on Polyglot, status = " + res.status + " " + res.statusText + ", call again in 3 sec")
          akka.pattern.after(3 seconds, using = Akka.system.scheduler)(checkForFileAndDownload((triesLeft - 1), url, outputStream))
        }
      }
    }

  /** 
   *  Uploads to Polyglot the file to be converted. Returns url of the converted file.
   */
  def getConvertedFileURL(filename: String, inputStream: java.io.InputStream, outputFormat: String): Future[String] = {
    //check that Polyglot credentials are defined
    if (!polyglotConvertURL.isDefined || !polyglotUser.isDefined || !polyglotPassword.isDefined) {
      throw new RuntimeException("Polyglot credentials not defined.")
    }

    // FIXME change to WS library call in play 2.5
    import org.apache.commons.io.IOUtils
    import java.io.File
    import java.io.FileOutputStream
    val PREFIX = "polyglot-clowder"
    val SUFFIX = ".tmp"
    val tempFile = File.createTempFile(PREFIX, SUFFIX)
    tempFile.deleteOnExit()
    try {
      val out = new FileOutputStream(tempFile)
      try
        IOUtils.copy(inputStream, out)
      finally if (out != null) out.close()
    }

    val encodedCredentials =
      new String(encodeBase64("%s:%s".format(polyglotUser.get, polyglotPassword.get).getBytes))

    val asyncHttpClient: AsyncHttpClient = WS.client.underlying
    val postBuilder = asyncHttpClient.preparePost(polyglotConvertURL.get + outputFormat)
    val builder = postBuilder
      .addHeader("Authorization", "Basic " + encodedCredentials)
      .addBodyPart(new FilePart("myFile", tempFile))
    val res = asyncHttpClient.executeRequest(builder.build()).get

    //get the url for the converted file on Polyglot
    if (res.getStatusCode != 200) {
      Logger.debug("Could not get url of converted file - status = " + res.getStatusCode + "  " + res.getStatusText)
      throw new RuntimeException("Could not connect to Polyglot. " + res.getStatusText)
    }
    //TODO: Find an easier way to get rid of html markup
    //result is an html link
    //<a href=http://the_url_string>http://the_url_string</a>
    val fileURL = res.getResponseBody.substring(res.getResponseBody.indexOf("http"), res.getResponseBody.indexOf(">"))
    fileURL
    Future.successful(fileURL)
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
    if (polyglotInputsURL.isDefined && polyglotUser.isDefined && polyglotPassword.isDefined) {
      //call polyglot server with authentication          
      WS.url(polyglotInputsURL.get + inputType)
        .withAuth(polyglotUser.get, polyglotPassword.get, WSAuthScheme.BASIC)
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
            outputFormats
        }
    } else {
      Logger.debug("Config params not defined")
      Future(None)
    }
  }
}
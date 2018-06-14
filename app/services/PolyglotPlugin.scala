package services

import java.io._

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
 * Polyglot Plugin
 *
 */
class PolyglotServiceImpl @Inject() (lifecycle: ApplicationLifecycle, configuration: Configuration, wsClient: WSClient)
  extends PolyglotService {

  val polyglotUser = configuration.get[String]("polyglot.username")
  val polyglotPassword = configuration.get[String]("polyglot.password")
  val polyglotConvertURL = configuration.get[String]("polyglot.convertURL")
  val polyglotInputsURL = configuration.get[String]("polyglot.inputsURL")

  //table to store output formats for each input format
  //store them in memory to avoind making a request to Polyglot each time
  val formatsTable = scala.collection.mutable.HashMap.empty[String, List[String]]

  Logger.debug("Starting Polyglot Plugin")

  lifecycle.addStopHook { () =>
    Logger.debug("Stopping Polyglot Plugin")
    Future.successful(())
  }

  /**
    * Keeps checking for the file on Polyglot server until the file is found or until too many tries.
    */
  def checkForFileAndDownload(triesLeft: Int, url: String, outputStream: OutputStream, file: java.io.File): Future[WSResponse] = {
    if (triesLeft == 0)
      Future.failed(throw new RuntimeException("Converted file not found."))
    else
      wsClient.url(url).withAuth(polyglotUser, polyglotPassword, WSAuthScheme.BASIC).get flatMap { res =>
        if (res.status == 200) {
          //this is the callback, runs after file exists is TRUE
          Logger.debug("File exists on polyglot. Will download now.")

          // Make the request
          val futureResponse: Future[WSResponse] =
            wsClient.url(url)
              .withAuth(polyglotUser, polyglotPassword, WSAuthScheme.BASIC)
              .withMethod("GET").stream()

          val downloadedFile: Future[File] = futureResponse.flatMap {
            res =>

              // The sink that writes to the output stream
              val sink = Sink.foreach[ByteString] { bytes =>
                outputStream.write(bytes.toArray)
              }

              // materialize and run the stream
              res.bodyAsSource.runWith(sink).andThen {
                case result =>
                  // Close the output stream whether there was an error or not
                  outputStream.close()
                  // Get the result or rethrow the error
                  result.get
              }.map(_ => file)
          }
          //Returning result. When it is mapped in the controller, the successful future is AFTER file has been downloaded on the Clowder server.
          futureResponse
        } else {
          Logger.debug("Checking if file exists on Polyglot, status = " + res.status + " " + res.statusText + ", call again in 3 sec")
          akka.pattern.after(3 seconds, using = Akka.system.scheduler)(checkForFileAndDownload((triesLeft - 1), url, outputStream))
        }
      }
  }

  /**
    * Uploads to Polyglot the file to be converted. Returns url of the converted file.
    */
  def getConvertedFileURL(filename: String, inputStream: java.io.InputStream, outputFormat: String): Future[String] = {

    // FIXME can we stream instead of writing to a temp file?
    import java.io.{File, FileOutputStream}

    import org.apache.commons.io.IOUtils
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

    val futureResponse = wsClient.url(polyglotConvertURL + outputFormat)
      .withAuth(polyglotUser, polyglotPassword, WSAuthScheme.BASIC)
      .post(Source(FilePart("hello", "hello.txt", Option("text/plain"), FileIO.fromPath(tempFile.toPath)) :: List()))

    futureResponse.flatMap {
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
    wsClient.url(polyglotInputsURL + inputType)
      .withAuth(polyglotUser, polyglotPassword, WSAuthScheme.BASIC)
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
  }
}
package services.filesystem

import java.io.{File, FileOutputStream, IOException, InputStream}

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{GetObjectRequest, PutObjectRequest}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.{AmazonClientException, AmazonServiceException, ClientConfiguration}
import com.google.inject.Inject
import org.apache.commons.io.IOUtils
import play.Logger
import play.api.Play
import services.ByteStorageService

/** Available configuration options for s3 storage */
object S3ByteStorageService {
  val ServiceEndpoint: String = "clowder.s3.serviceEndpoint"
  val BucketName: String = "clowder.s3.bucketName"
  val AccessKey: String = "clowder.s3.accessKey"
  val SecretKey: String = "clowder.s3.secretKey"
}

/**
  * A ByteStorageService for Clowder that enables use of S3-compatible
  * object stores to serve as the file backing for Clowder. This allows
  * you to use an S3 bucket on AWS or Minio to store your files.
  *
  *
  * Available Configuration Options:
  *    clowder.s3.serviceEndpoint - Host/port of the service to use for storage
  *    clowder.s3.bucketName - the name of the bucket that should be used to store files
  *    clowder.s3.accessKey - access key with which to access the bucket
  *    clowder.s3.secretKey - secret key associated with the access key above
  *
  *
  * @author Mike Lambert
  *
  */
class S3ByteStorageService @Inject()() extends ByteStorageService {
  val tmpFileAbsPath = "/tmp/tmp-s3-upload.tmp"

  def saveToTmpFile(inputStream: InputStream): File = {
    val tmpFile = new File(tmpFileAbsPath)
    try {
      // Copy the contents of our InputStream to a temp file
      val outputStream = new FileOutputStream(tmpFile)
      IOUtils.copy(inputStream, outputStream)
      if (outputStream != null) {
        outputStream.close()
      }
    } catch {
      case ioe: IOException => handleIOE(ioe)
      case _: Throwable => handleUnknownError(_)
    }
    return tmpFile
  }

  /**
    * Grabs config parameters from Clowder to return a
    * AmazonS3 pointing at the configured service endpoint.
    */
  def s3Bucket(): Option[AmazonS3] = {
    Play.current.configuration.getString(S3ByteStorageService.ServiceEndpoint) match {
      case None => {
        Logger.error("No service endpoint provided in " + S3ByteStorageService.ServiceEndpoint)
        throw new RuntimeException("No service endpoint provided in " + S3ByteStorageService.ServiceEndpoint)
      }
      case Some(serviceEndpoint) => Play.current.configuration.getString(S3ByteStorageService.AccessKey) match {
        case None => {
          Logger.error("No access key provided in " + S3ByteStorageService.AccessKey)
          throw new RuntimeException("No access key provided in " + S3ByteStorageService.AccessKey)
        }
        case Some(accessKey) => Play.current.configuration.getString(S3ByteStorageService.SecretKey) match {
          case None => {
            Logger.error("No secret key provided in " + S3ByteStorageService.SecretKey)
            throw new RuntimeException("No secret key provided in " + S3ByteStorageService.SecretKey)
          }
          case Some(secretKey) => {
            val credentials = new BasicAWSCredentials(accessKey, secretKey)
            val clientConfiguration = new ClientConfiguration
            clientConfiguration.setSignerOverride("AWSS3V4SignerType")

            Logger.info("Created S3 Client for " + serviceEndpoint)

            return Option(AmazonS3ClientBuilder.standard()
              // NOTE: Region is ignored for MinIO case?
              .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, Regions.US_EAST_1.name()))
              .withPathStyleAccessEnabled(true)
              .withClientConfiguration(clientConfiguration)
              .withCredentials(new AWSStaticCredentialsProvider(credentials))
              .build())
          }
        }
      }
    }
  }

  def handleIOE(err: IOException) = {
    Logger.error("IOException occurred in the S3ByteStorageService: " + err)
  }

  def handleUnknownError(err: Exception = null) = {
    if (err != null) {
      Logger.error("An unknown error occurred in the S3ByteStorageService: " + err.toString())
    } else {
      Logger.error("An unknown error occurred in the S3ByteStorageService.")
    }
  }

  def handleACE(ace: AmazonClientException) = {
    Logger.error("Caught an AmazonClientException, which " + "means the client encountered " + "an internal error while trying to " + "communicate with S3, " + "such as not being able to access the network.")
    Logger.error("Error Message: " + ace.getMessage)
  }

  def handleASE(ase: AmazonServiceException) = {
    Logger.error("Caught an AmazonServiceException, which " + "means your request made it " + "to Amazon S3, but was rejected with an error response" + " for some reason.")
    Logger.error("Error Message:    " + ase.getMessage)
    Logger.error("HTTP Status Code: " + ase.getStatusCode)
    Logger.error("AWS Error Code:   " + ase.getErrorCode)
    Logger.error("Error Type:       " + ase.getErrorType)
    Logger.error("Request ID:       " + ase.getRequestId)
  }

  /**
    * Store bytes to the specified path within the configured S3 bucket.
    *
    * @param inputStream stream of bytes to save to the bucket
    * @param ignored     unused parameter in this context
    * @return
    */
  def save(inputStream: InputStream, ignored: String): Option[(String, Long)] = {
    this.s3Bucket() match {
      case None => Logger.error("Failed creating s3 client.")
      case Some(client) => Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
        case None => Logger.error("Failed deleting bytes: failed to find configured S3 bucketName.")
        case Some(bucketName) => {
          val tmpFile = saveToTmpFile(inputStream)
          try {

            Logger.info("Saving file to: /" + bucketName)

            // TODO: How to build up a unique path based on the file/uploader?
            val targetPath = "testing"

            // Upload temp file to S3 bucket
            client.putObject(new PutObjectRequest(bucketName, targetPath, tmpFile))

            Logger.info("File saved to: /" + bucketName + "/" + targetPath)

            // Clean-up temp file
            tmpFile.delete()

            return Option(targetPath, 1234)

            // TODO: Verify transfered bytes with MD5?
          } catch {
            case ase: AmazonServiceException => handleASE(ase)
            case ace: AmazonClientException => handleACE(ace)
            case ioe: IOException => handleIOE(ioe)
            case _: Throwable => handleUnknownError(_)
          }

          // Clean-up temp file
          tmpFile.delete()
        }
      }
    }
    None
  }

  /**
    * Given a path, retrieve the bytes located at that path inside the configured S3 bucket.
    *
    * @param path the path of the file to load
    * @param ignored unused parameter in this context
    * @return
    */
  def load(path: String, ignored: String): Option[InputStream] = {
    this.s3Bucket() match {
      case None => Logger.error("Failed creating s3 client.")
      case Some(client) => Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
        case None => Logger.error("Failed fetching bytes: failed to find configured S3 bucketName.")
        case Some(bucketName) => {
          Logger.info("Loading file from: /" + bucketName + "/" + path)
          try {
            // Download object from S3 bucket
            val rangeObjectRequest = new GetObjectRequest(bucketName, path)
            val objectPortion = client.getObject(rangeObjectRequest)

            return Option(objectPortion.getObjectContent)
          } catch {
            case ase: AmazonServiceException => handleASE(ase)
            case ace: AmazonClientException => handleACE(ace)
            case ioe: IOException => handleIOE(ioe)
            case _: Throwable => handleUnknownError(_)
          }
        }
      }
    }
    None
  }

  /**
    * Given a path, delete the file located at the path within the configured S3 bucket.
    *
    * @param path    the path of the file inside the bucket
    * @param ignored unused parameter in this context
    * @return
    */
  def delete(path: String, ignored: String): Boolean = {
    this.s3Bucket() match {
      case None => Logger.error("Failed creating s3 client.")
      case Some(client) => Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
        case None => Logger.error("Failed deleting bytes: failed to find configured S3 bucketName.")
        case Some(bucketName) => {
          // delete the bytes
          Logger.info("Removing file at: /" + bucketName + "/" + path)
          try {
            // Delete object from S3 bucket
            client.deleteObject(bucketName, path)

            // TODO: Perform a GET to verify deletion??

            return true
          } catch {
            case ase: AmazonServiceException => handleASE(ase)
            case ace: AmazonClientException => handleACE(ace)
            case ioe: IOException => handleIOE(ioe)
            case _: Throwable => handleUnknownError(_)
          }
          return false
        }
      }
    }
    return false
  }
}

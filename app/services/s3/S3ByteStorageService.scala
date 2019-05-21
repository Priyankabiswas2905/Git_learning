package services.s3

import java.io.{File, FileOutputStream, IOException, InputStream}
import java.net.URI
import java.nio.ByteBuffer
import java.util.UUID

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.apache.{ApacheHttpClient, ProxyConfiguration}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsSessionCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, DeleteObjectRequest, GetObjectRequest, HeadBucketRequest, PutObjectRequest}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.core.exception.{SdkClientException, SdkException}
import com.google.inject.Inject
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.ByteArrayOutputStream
import play.Logger
import play.api.Play
import services.ByteStorageService
import software.amazon.awssdk.core.sync.{RequestBody, ResponseTransformer}
import software.amazon.awssdk.http.AbortableInputStream

/** Available configuration options for s3 storage */
object S3ByteStorageService {
  val ServiceEndpoint: String = "clowder.s3.serviceEndpoint"
  val BucketName: String = "clowder.s3.bucketName"
  val AccessKey: String = "clowder.s3.accessKey"
  val SecretKey: String = "clowder.s3.secretKey"
  val Region: String = "clowder.s3.region"
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
  *    clowder.s3.region - the region where your S3 bucket lives (currently unused)
  *
  *
  * @author Mike Lambert
  *
  */
class S3ByteStorageService @Inject()() extends ByteStorageService {

  // Verify access to the configured bucket, create it if it does not exist
  // NOTE: Use a single thread when verifying/creating bucket
  this.synchronized {
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case Some(bucketName) => {
        try {
          // Validate configuration by checking for bucket existence/access
          val builder = HeadBucketRequest.builder
          val checkBucketReq = builder.bucket(bucketName).build
          this.s3Bucket.headBucket(checkBucketReq)
        } catch {
          case sdke @ (_: SdkException | _: SdkClientException) => {
            if (sdke.getMessage.indexOf("Status Code: 404") != -1) {
              Logger.warn("Configured S3 bucket does not exist - creating it now...")
              try {
                // Bucket does not exist - create the bucket
                val builder = CreateBucketRequest.builder
                val createBucketReq = builder.bucket(bucketName).build
                this.s3Bucket.createBucket(createBucketReq)
              } catch {
                // Bucket could not be created - abort
                case _: Throwable => throw new RuntimeException("Bad S3 configuration: Bucket does not exist and could not be created.")
              }
            } else if (sdke.getMessage.indexOf("Status Code: 403") != -1) {
              // Bucket exists, but you do not have permission to access it
              throw new RuntimeException("Bad S3 configuration: You do not have access to the configured S3 bucket.")
            } else {
              // Unknown error - print status code for further investigation
              Logger.error(sdke.getLocalizedMessage)
              throw new RuntimeException("Bad S3 configuration: an unhandled error has occurred.")
            }
          }
          case _: Throwable => throw new RuntimeException("Bad S3 configuration: an unknown error has occurred.")
        }
      }
      case _ => throw new RuntimeException("Bad S3 configuration: verify that you have set all configuration options.")
    }
  }

  /**
    * Grabs config parameters from Clowder to return a
    * AmazonS3 pointing at the configured service endpoint.
    */
  def s3Bucket(): S3Client = {
    (Play.current.configuration.getString(S3ByteStorageService.ServiceEndpoint),
      Play.current.configuration.getString(S3ByteStorageService.AccessKey),
        Play.current.configuration.getString(S3ByteStorageService.SecretKey)) match {
          case (Some(serviceEndpoint), Some(accessKey), Some(secretKey)) => {

            Logger.debug("Created S3 Client for " + serviceEndpoint)

            val region = Play.current.configuration.getString(S3ByteStorageService.Region) match {
              case Some(region) => Region.of(region)
              case _ => Region.US_EAST_1
            }

            val credentials = AwsBasicCredentials.create(accessKey, secretKey)
            val proxyConfig = ProxyConfiguration.builder
            val httpClientBuilder = ApacheHttpClient.builder.proxyConfiguration(proxyConfig.build)
            val overrideConfig = ClientOverrideConfiguration.builder

            // NOTE: Region is ignored for MinIO case
            return S3Client.builder
              .credentialsProvider(StaticCredentialsProvider.create(credentials))
              .httpClientBuilder(httpClientBuilder)
              .endpointOverride(new URI(serviceEndpoint))
              .overrideConfiguration(overrideConfig.build)
              .region(region).build
          }
          case _ => throw new RuntimeException("Bad S3 configuration: verify that you have set all configuration options.")
        }
  }

  /**
    * Store bytes to the specified path within the configured S3 bucket.
    *
    * @param inputStream stream of bytes to save to the bucket
    * @param ignored     unused parameter in this context
    * @return
    */
  def save(inputStream: InputStream, ignored: String): Option[(String, Long)] = {
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case None => Logger.error("Failed deleting bytes: failed to find configured S3 bucketName.")
      case Some(bucketName) => {
        try {
          Logger.debug("Saving file to: /" + bucketName)

          // TODO: How to build up a unique path based on the file/uploader?
          val targetPath = UUID.randomUUID().toString
          val length = inputStream.available()
          val builder = PutObjectRequest.builder
          val request = builder.bucket(bucketName).key(targetPath).build
          // Upload temp file to S3 bucket
          val inputBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream))
          this.s3Bucket.putObject(request, RequestBody.fromByteBuffer(inputBytes))

          Logger.debug("File saved to: /" + bucketName + "/" + targetPath)

          return Option((targetPath, length))

          // TODO: Verify transfered bytes with MD5?
        } catch {
          case sdkce: SdkClientException => handleSDKCE(sdkce)
          case sdke: SdkException => handleSDKE(sdke)
          case ioe: IOException => handleIOE(ioe)
          case _: Throwable => handleUnknownError(_)
        }
      }
    }

    // Return None (in case of failure)
    None
  }

  /**
    * Given a path, retrieve the bytes located at that path inside the configured S3 bucket.
    *
    * @param path    the path of the file to load from the bucket
    * @param ignored unused parameter in this context
    * @return
    */
  def load(path: String, ignored: String): Option[InputStream] = {
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case None => Logger.error("Failed fetching bytes: failed to find configured S3 bucketName.")
      case Some(bucketName) => {
        Logger.debug("Loading file from: /" + bucketName + "/" + path)
        try {
          // Download object from S3 bucket
          val builder = GetObjectRequest.builder
          val request = builder.bucket(bucketName).key(path).build
          val objectPortion = this.s3Bucket.getObject(request)
          //, ResponseTransformer.toInputStream
          return Option(objectPortion)
        } catch {
          case sdkce: SdkClientException => handleSDKCE(sdkce)
          case sdke: SdkException => handleSDKE(sdke)
          case ioe: IOException => handleIOE(ioe)
          case _: Throwable => handleUnknownError(_)
        }
      }
    }

    // Return None (in case of failure)
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
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case None => Logger.error("Failed deleting bytes: failed to find configured S3 bucketName.")
      case Some(bucketName) => {
        // delete the bytes
        Logger.debug("Removing file at: /" + bucketName + "/" + path)
        try {
          // Delete object from S3 bucket
          val builder = DeleteObjectRequest.builder
          val deleteRequest = builder.bucket(bucketName).key(path).build
          this.s3Bucket.deleteObject(deleteRequest)

          // TODO: Perform an additional GET to verify deletion?

          return true
        } catch {
          case sdkce: SdkClientException => handleSDKCE(sdkce)
          case sdke: SdkException => handleSDKE(sdke)
          case ioe: IOException => handleIOE(ioe)
          case _: Throwable => handleUnknownError(_)
        }
      }
    }

    // Return false (in case of failure)
    return false
  }

  /* Reusable handlers for various Exception types */
  def handleUnknownError(err: Exception = null) = {
    if (err != null) {
      Logger.error("An unknown error occurred in the S3ByteStorageService: " + err.toString)
    } else {
      Logger.error("An unknown error occurred in the S3ByteStorageService.")
    }
  }

  def handleIOE(err: IOException) = {
    Logger.error("IOException occurred in the S3ByteStorageService: " + err)
  }

  def handleSDKCE(sdkce: SdkClientException) = {
    Logger.error("Caught an SdkClientException, which " + "means the client encountered " + "an error while trying to " + "communicate with S3.")
    Logger.error("Error Message: " + sdkce.getMessage)
  }

  def handleSDKE(sdke: SdkException) = {
    Logger.error("Caught an SdkException, which linkely " + "means the server encountered " + "an error while trying to " + "process this request.")
    Logger.error("Error Message: " + sdke.getMessage)
  }
}

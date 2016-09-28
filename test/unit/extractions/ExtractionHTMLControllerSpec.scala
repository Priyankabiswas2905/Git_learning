package integration

import org.scalatest.Assertions._
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar._
import org.mockito.Mockito.doNothing
import services.ExtractorService
import services.ExtractionService
import services.ExtractionRequestsService
import services.PreviewService
import services.RdfSPARQLService
import services.ThumbnailService
import services.FileService
import play.api.GlobalSettings
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.Logger
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import unit.UnitSpec
import org.scalatestplus.play.OneAppPerSuite
import org.scalatest.DoNotDiscover
import org.scalatestplus.play.PlaySpec
import play.api.Play

/*
 * Integration/Unit Test for Extractions Api Controller 
 * @author Smruti Padhy 
 * 
 */

class ExtractionAPIControllerSpec extends PlaySpec with OneAppPerSuite {
  
  val excludedPlugins = List(
    "services.mongodb.MongoSalatPlugin",
    "com.typesafe.plugin.CommonsMailerPlugin",
    "services.mongodb.MongoDBAuthenticatorStore",
    "securesocial.core.DefaultIdGenerator",
    "securesocial.core.providers.utils.DefaultPasswordValidator",
    "services.ss.SecureSocialTemplatesPlugin",
    "services.mongodb.MongoUserService",
    "securesocial.core.providers.utils.BCryptPasswordHasher",
    "securesocial.core.providers.UsernamePasswordProvider",
    "services.RabbitmqPlugin",
    "services.VersusPlugin")

  implicit override lazy val app: FakeApplication = FakeApplication(withoutPlugins = excludedPlugins, withGlobal = Some(new GlobalSettings() {
		def onStart(app: App) { println("Fake Application Started") }
	}))

  val mockfiles = mock[FileService]
  val mockExtractions = mock[ExtractionService]
  val mockDTS = mock[ExtractionRequestsService]
  val mockExtractors = mock[ExtractorService]
  val mockPreviews = mock[PreviewService]
  val mockRdf = mock[RdfSPARQLService]
  val mockthumbnails = mock[ThumbnailService]

  when(mockExtractors.getExtractorNames).thenReturn(List("ncsa.cv.face", "ncsa.ocr"))
  when(mockExtractors.getExtractorServerIPList).thenReturn(List("dts1.ncsa.illinois.edu", "141.142.220.244"))
  when(mockExtractors.getExtractorInputTypes).thenReturn(List("image", "text"))
  doNothing().when(mockExtractors).insertExtractorNames(List("ncsa.cv.face", "ncsa.ocr"))
  doNothing().when(mockExtractors).insertServerIPs(List("dts1.ncsa.illinois.edu", "141.142.220.244"))
  doNothing().when(mockExtractors).insertInputTypes(List("image", "text"))

  "The OneAppPerSuite trait for Extraction API Controller get actions" must {
     "return List of Extractors Names" in {
      val extractions_apicontroller = new api.Extractions(mockfiles, mockExtractions, mockDTS, mockExtractors, mockPreviews, mockRdf, mockthumbnails)
      val resultExNames = extractions_apicontroller.getExtractorNames.apply(FakeRequest())
      contentType(resultExNames) mustEqual Some("application/json")
      contentAsString(resultExNames) must include ("Extractors")
      info("Extractors names "+contentAsString(resultExNames))
     }

    "return List of Extractors' Servers IPs/hostname" in {
      val extractions_apicontroller = new api.Extractions(mockfiles, mockExtractions, mockDTS, mockExtractors, mockPreviews, mockRdf, mockthumbnails)
      val resultExIPs = extractions_apicontroller.getExtractorServersIP.apply(FakeRequest())
      contentType(resultExIPs) mustEqual Some("application/json")
      contentAsString(resultExIPs) must include ("Servers")
      info("Extractors Servers IPs: "+ contentAsString(resultExIPs))
      }

    "return List of Extractors supported Input Types" in {
      val extractions_apicontroller = new api.Extractions(mockfiles, mockExtractions, mockDTS, mockExtractors, mockPreviews, mockRdf, mockthumbnails)
      val resultExInputTypes = extractions_apicontroller.getExtractorInputTypes.apply(FakeRequest())
      contentType(resultExInputTypes) mustEqual Some("application/json")
      contentAsString(resultExInputTypes) must include ("InputTypes")
      info("Extractors Supported Input Types: "+ contentAsString(resultExInputTypes))
     }
   }

}
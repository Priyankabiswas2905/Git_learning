package integration

import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.Logger
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.Play
import org.scalatest._




class APIFilesTestSuite extends Suites (
		new FilesAPIAppSpec
	) with OneAppPerSuite
{
// Override app if you need a FakeApplication with other than non-default parameters.
   implicit override lazy val app: FakeApplication =
     FakeApplication(additionalConfiguration = Map("ehcacheplugin" -> "disabled", "withoutPlugins" -> List(
    "services.RabbitmqPlugin",
    "services.VersusPlugin")))

}

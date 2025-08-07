/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apiplatforminboundsoap.connectors

import scala.io.Source
import scala.xml.{Elem, XML}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.ExternalWireMockSupport

import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendResult, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.wiremockstubs.ApiPlatformOutboundSoapStub
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

class CrdlOrchestratorConnectorISpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite
    with ExternalWireMockSupport with ApiPlatformOutboundSoapStub with Ics2XmlHelper {
  override implicit lazy val app: Application = appBuilder.build()
  implicit val hc: HeaderCarrier              = HeaderCarrier()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"                              -> false,
        "auditing.enabled"                             -> false,
        "microservice.services.crdl-orchestrator.host" -> externalWireMockHost,
        "microservice.services.crdl-orchestrator.port" -> externalWireMockPort
      )

  trait Setup {
    val underTest: CrdlOrchestratorConnector = app.injector.instanceOf[CrdlOrchestratorConnector]
    val crdlRequestBody: Elem                = readFromFile("requests/crdl/crdl-request.xml")
    val targetPath                           = "/crdl/incoming"
    val addedHeaders                         = Seq.empty

    def readFromFile(fileName: String) = {
      XML.load(Source.fromResource(fileName).bufferedReader())
    }
  }

  "postMessage" should {

    "return success status when returned by the CRDL orchestrator service" in new Setup {
      primeStubForSuccess(crdlRequestBody, OK, path = targetPath)

      val result: SendResult = await(underTest.postMessage(crdlRequestBody, addedHeaders))

      result shouldBe SendSuccess(OK)
      verifyRequestBody(crdlRequestBody, path = targetPath)
    }

    "return error status returned by the CRDL orchestrator service" in new Setup {
      val expectedStatus: Int = INTERNAL_SERVER_ERROR
      primeStubForSuccess(crdlRequestBody, expectedStatus, path = targetPath)

      val result: SendResult = await(underTest.postMessage(crdlRequestBody, addedHeaders))

      result shouldBe SendFailExternal(s"POST of 'http://$externalWireMockHost:$externalWireMockPort$targetPath' returned $expectedStatus. Response body: ''", expectedStatus)
    }
  }
}

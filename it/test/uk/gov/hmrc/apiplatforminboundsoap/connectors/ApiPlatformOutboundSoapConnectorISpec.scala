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

import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.ExternalWireMockSupport

import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendResult, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.stubs.ApiPlatformOutboundSoapStub
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

class ApiPlatformOutboundSoapConnectorISpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite
    with ExternalWireMockSupport with ApiPlatformOutboundSoapStub with Ics2XmlHelper {
  override implicit lazy val app: Application = appBuilder.build()
  implicit val hc: HeaderCarrier              = HeaderCarrier()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"                                       -> false,
        "auditing.enabled"                                      -> false,
        "microservice.services.api-platform-outbound-soap.host" -> externalWireMockHost,
        "microservice.services.api-platform-outbound-soap.port" -> externalWireMockPort
      )

  trait Setup {
    val underTest: ApiPlatformOutboundSoapConnector = app.injector.instanceOf[ApiPlatformOutboundSoapConnector]
    val codRequestBody: Elem                        = readFromFile("acknowledgement-requests/cod_request.xml")
    val expectedHeaders                             = forwardedHeaders(codRequestBody)
    val acknowledgementPath                         = "/acknowledgement"

    def readFromFile(fileName: String) = {
      XML.load(Source.fromResource(fileName).bufferedReader())
    }

    def forwardedHeaders(xmlBody: Elem) = Seq[(String, String)]("x-soap-action" -> getSoapAction(xmlBody).getOrElse(""))

  }

  "postMessage" should {

    "return success status (for COD) when returned by the outbound soap service" in new Setup {
      primeStubForSuccess(codRequestBody, OK, path = acknowledgementPath)

      val result: SendResult = await(underTest.postMessage(codRequestBody))

      result shouldBe SendSuccess
      verifyRequestBody(codRequestBody, path = acknowledgementPath)
      verifyHeader(expectedHeaders.head._1, expectedHeaders.head._2, path = acknowledgementPath)
    }

    "return error status returned by the outbound soap service" in new Setup {
      val expectedStatus: Int = INTERNAL_SERVER_ERROR
      primeStubForSuccess(codRequestBody, expectedStatus, path = acknowledgementPath)

      val result: SendResult = await(underTest.postMessage(codRequestBody))

      result shouldBe SendFailExternal(expectedStatus)
      verifyHeader(expectedHeaders.head._1, expectedHeaders.head._2, path = acknowledgementPath)
    }

    "return error status when soap fault is returned by the outbound soap service" in new Setup {
      val responseBody = "<Envelope><Body>foobar</Body></Envelope>"
      Seq(Fault.CONNECTION_RESET_BY_PEER, Fault.EMPTY_RESPONSE, Fault.MALFORMED_RESPONSE_CHUNK, Fault.RANDOM_DATA_THEN_CLOSE) foreach { fault =>
        primeStubForFault(codRequestBody, responseBody, fault, path = acknowledgementPath)

        val result: SendResult = await(underTest.postMessage(codRequestBody))

        result shouldBe SendFailExternal(INTERNAL_SERVER_ERROR)
      }
    }
  }
}

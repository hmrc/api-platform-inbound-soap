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

import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.ExternalWireMockSupport

import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendResult, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.wiremockstubs.ExternalServiceStub

class EoriServiceConnectorISpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite
    with ExternalWireMockSupport with ExternalServiceStub {
  override implicit lazy val app: Application = appBuilder.build()
  implicit val hc: HeaderCarrier              = HeaderCarrier()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"                         -> false,
        "auditing.enabled"                        -> false,
        "microservice.services.eori-service.host" -> externalWireMockHost,
        "microservice.services.eori-service.port" -> externalWireMockPort
      )

  trait Setup {
    val underTest: EoriServiceConnector = app.injector.instanceOf[EoriServiceConnector]
    val requestBody                     = <foo>bar</foo>
    val responseBody                    = <zim>job</zim>
    val targetPath                      = "/taxud/crs/receiveDataChangeEvents/v1"
    val addedHeaders                    = Seq.empty
  }

  "postMessage" should {

    "return success status when returned by the EORI service" in new Setup {
      primeStubForXMLSuccess(requestBody, responseBody, OK, path = targetPath)

      val result: SendResult = await(underTest.postMessage(requestBody, addedHeaders))

      result shouldBe SendSuccess(OK, responseBody.mkString)
      verifyXMLRequestBody(requestBody, path = targetPath)
    }

    "return error status returned by the EORI service" in new Setup {
      val expectedStatus: Int = INTERNAL_SERVER_ERROR
      primeStubForXMLSuccess(requestBody, responseBody, expectedStatus, path = targetPath)

      val result: SendResult = await(underTest.postMessage(requestBody, addedHeaders))

      result shouldBe SendFailExternal(
        s"POST of 'http://$externalWireMockHost:$externalWireMockPort$targetPath' returned $expectedStatus. Response body: '${responseBody.mkString}'",
        expectedStatus
      )
    }

    "return error status when soap fault is returned by the EORI service" in new Setup {
      Seq(
        Fault.CONNECTION_RESET_BY_PEER -> "Connection reset",
        Fault.EMPTY_RESPONSE           -> "Remotely closed",
        Fault.MALFORMED_RESPONSE_CHUNK -> "Remotely closed",
        Fault.RANDOM_DATA_THEN_CLOSE   -> "Remotely closed"
      ) foreach { input =>
        primeStubForFault(requestBody.mkString, input._1, targetPath)

        val result: SendResult = await(underTest.postMessage(requestBody, addedHeaders))

        result shouldBe SendFailExternal(s"${input._2}", INTERNAL_SERVER_ERROR)
      }
    }
  }
}

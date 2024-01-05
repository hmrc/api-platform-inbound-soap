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
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Headers
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendResult, SendSuccess, SoapRequest}
import uk.gov.hmrc.apiplatforminboundsoap.support.ImportControlInboundSoapStub
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

class InboundConnectorISpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with WireMockSupport with ImportControlInboundSoapStub {
  override implicit lazy val app: Application = appBuilder.build()
  implicit val hc: HeaderCarrier              = HeaderCarrier()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"  -> false,
        "auditing.enabled" -> false
      )

  trait Setup {
    val headers                     = Headers("key" -> "value")
    val underTest: InboundConnector = app.injector.instanceOf[InboundConnector]
  }

  "postMessage" should {
    val message = SoapRequest("<Envelope><Body>foobar</Body></Envelope>", wireMockUrl)

    "return successful statuses returned by the internal service" in new Setup {
      val expectedStatus: Int = OK
      primeStubForSuccess(message.soapEnvelope, expectedStatus)

      val result: SendResult = await(underTest.postMessage(message, headers))

      result shouldBe SendSuccess
    }

    "return error statuses returned by the internal service" in new Setup {
      val expectedStatus: Int = INTERNAL_SERVER_ERROR
      primeStubForSuccess(message.soapEnvelope, expectedStatus)

      val result: SendResult = await(underTest.postMessage(message, headers))

      result shouldBe SendFail(expectedStatus)
    }

    "return error status when soap fault is returned by the internal service" in new Setup {
      Seq(Fault.CONNECTION_RESET_BY_PEER, Fault.EMPTY_RESPONSE, Fault.MALFORMED_RESPONSE_CHUNK, Fault.RANDOM_DATA_THEN_CLOSE) foreach { fault =>
        primeStubForFault(message.soapEnvelope, fault)

        val result: SendResult = await(underTest.postMessage(message, headers))

        result shouldBe SendFail(INTERNAL_SERVER_ERROR)
      }
    }

    "send the given message to the internal service" in new Setup {
      primeStubForSuccess(message.soapEnvelope, OK)

      await(underTest.postMessage(message, headers))

      verifyRequestBody(message.soapEnvelope)
    }
  }
}

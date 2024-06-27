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

import scala.concurrent.ExecutionContext.Implicits.global

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import play.api.http.Status.OK
import play.api.test.Helpers.{INTERNAL_SERVER_ERROR, await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{ExternalWireMockSupport, HttpClientV2Support}

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendResult, SendSuccess, SoapRequest}

class InboundConnectorSpec extends AnyWordSpec with Matchers with HttpClientV2Support with ExternalWireMockSupport with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {
    val appConfigMock: AppConfig = mock[AppConfig]
    val headers                  = Seq[(String, String)]("key" -> "value")
    val underTest                = new InboundConnector(httpClientV2, appConfigMock)
  }

  "InboundConnector" should {
    "return valid status code if http post returns 2xx" in new Setup {
      val soapRequest: SoapRequest = SoapRequest("<xml>stuff</xml>", s"$externalWireMockUrl")
      stubFor(post(urlEqualTo("/")).willReturn(aResponse().withStatus(OK)))
      val result: SendResult       = await(underTest.postMessage(soapRequest, headers))
      result shouldBe SendSuccess

    }

    "return valid status code if http post returns 5xx" in new Setup {
      val soapRequest: SoapRequest = SoapRequest("<xml>stuff</xml>", s"$externalWireMockUrl")
      stubFor(post(urlEqualTo("/")).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))
      val result: SendResult       = await(underTest.postMessage(soapRequest, headers))
      result shouldBe SendFail(INTERNAL_SERVER_ERROR)
    }

    "return valid status code if http post returns NonFatal errors" in new Setup {
      val soapRequest: SoapRequest = SoapRequest("<xml>stuff</xml>", s"$externalWireMockUrl")
      stubFor(post(urlEqualTo("/")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))
      val result: SendResult       = await(underTest.postMessage(soapRequest, headers))
      result shouldBe SendFail(INTERNAL_SERVER_ERROR)
    }
  }
}

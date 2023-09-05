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
import scala.concurrent.Future.{failed, successful}

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Headers
import play.api.test.Helpers.{INTERNAL_SERVER_ERROR, await, defaultAwaitTimeout}
import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendResult, SendSuccess, SoapRequest}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

class InboundConnectorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure()
    .build()

  trait Setup {
    val appConfigMock: AppConfig   = mock[AppConfig]
    val headers                    = Headers("key" -> "value")
    val mockHttpClient: HttpClient = mock[HttpClient]
    val underTest                  = new InboundConnector(mockHttpClient)
  }

  "InboundConnector" should {
    "return valid status code if http post returns 2xx" in new Setup {
      val soapRequest: SoapRequest = SoapRequest("<xml>stuff</xml>", "some url")
      when(mockHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](*, *, *)(*, *, *))
        .thenReturn(successful(Right(HttpResponse(OK, ""))))
      val result: SendResult       = await(underTest.postMessage(soapRequest, headers))
      result shouldBe SendSuccess
    }

    "return valid status code if http post returns 5xx" in new Setup {
      val soapRequest: SoapRequest = SoapRequest("<xml>stuff</xml>", "some url")
      when(mockHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](*, *, *)(*, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("unexpected error", INTERNAL_SERVER_ERROR))))
      val result: SendResult       = await(underTest.postMessage(soapRequest, headers))
      result shouldBe SendFail(INTERNAL_SERVER_ERROR)
    }

    "return valid status code if http post returns NonFatal errors" in new Setup {
      val soapRequest: SoapRequest = SoapRequest("<xml>stuff</xml>", "some url")

      when(mockHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](*, *, *)(*, *, *))
        .thenReturn(failed(play.shaded.ahc.org.asynchttpclient.exception.RemotelyClosedException.INSTANCE))
      val result: SendResult = await(underTest.postMessage(soapRequest, headers))
      result shouldBe SendFail(INTERNAL_SERVER_ERROR)
    }
  }
}

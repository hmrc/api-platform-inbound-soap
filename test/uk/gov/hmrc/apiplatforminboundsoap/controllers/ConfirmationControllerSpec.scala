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

package uk.gov.hmrc.apiplatforminboundsoap.controllers

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.io.Source
import scala.xml.{Elem, XML}

import org.apache.pekko.stream.Materializer
import org.mockito.captor.{ArgCaptor, Captor}
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.ApiPlatformOutboundSoapConnector
import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.VerifyJwtTokenAction
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendSuccess}

class ConfirmationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    private val verifyJwtTokenAction = app.injector.instanceOf[VerifyJwtTokenAction]
    val mockOutboundConnector        = mock[ApiPlatformOutboundSoapConnector]
    val controller                   = new ConfirmationController(mockOutboundConnector, Helpers.stubControllerComponents(), verifyJwtTokenAction)

    val xRequestIdHeaderValue = randomUUID.toString()

    val headers = Headers(
      "Host"         -> "localhost",
      "x-request-id" -> xRequestIdHeaderValue,
      "Content-Type" -> "text/xml"
    )

    def readFromFile(fileName: String) = {
      XML.load(Source.fromResource(fileName).bufferedReader())
    }

    val codRequestBody: Elem = readFromFile("acknowledgement-requests/cod_request.xml")
    val coeRequestBody: Elem = readFromFile("acknowledgement-requests/coe_request.xml")
  }

  "POST acknowledgement endpoint with no authorisation header" should {
    "return 403" in new Setup {
      val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers)
        .withBody(codRequestBody)

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.UNAUTHORIZED
    }
  }

  "POST acknowledgement endpoint with empty authorisation header" should {
    "return 403" in new Setup {
      val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add("Authorization" -> "Bearer"))
        .withBody(codRequestBody)

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.UNAUTHORIZED
    }
  }

  "POST acknowledgement endpoint with exp claim in the past" should {
    "return 403" in new Setup {
      val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(
          "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2OTIzNTkxNDJ9.SLEu_OlkHea19WwAYq_wG5nRJ43-uBv013QH3U_Gqvs"
        ))
        .withBody(codRequestBody)

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.UNAUTHORIZED
    }
  }

  "POST acknowledgement endpoint with no request body" should {
    "return 400" in new Setup {
      val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(
          "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwMDc5NzcwNzd9.bgdyMvTvicf5FvAlQXN-311k0WTZg0-72wqR4hb66dQ"
        ))

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "POST acknowledgement endpoint with valid authorisation header and COD request body" should {
    "return 200" in new Setup {
      val fakeRequest                    = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(
          "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwMDc5NzcwNzd9.bgdyMvTvicf5FvAlQXN-311k0WTZg0-72wqR4hb66dQ"
        ))
        .withBody(codRequestBody)
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      when(mockOutboundConnector.postMessage(xmlRequestCaptor)(*)).thenReturn(successful(SendSuccess))

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.OK
      verify(mockOutboundConnector).postMessage(*)(*)
      xmlRequestCaptor hasCaptured codRequestBody
    }
  }

  "POST acknowledgement endpoint with valid authorisation header and COE request body" should {
    "return 200" in new Setup {
      val fakeRequest                    = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(
          "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwMDc5NzcwNzd9.bgdyMvTvicf5FvAlQXN-311k0WTZg0-72wqR4hb66dQ"
        ))
        .withBody(coeRequestBody)
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      when(mockOutboundConnector.postMessage(xmlRequestCaptor)(*)).thenReturn(successful(SendSuccess))

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.OK
      verify(mockOutboundConnector).postMessage(*)(*)
      xmlRequestCaptor hasCaptured coeRequestBody
    }
  }

  "POST acknowledgement endpoint with valid authorisation header and COE request body but outbound connector returns 500" should {
    "return 200" in new Setup {
      val fakeRequest                    = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(
          "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwMDc5NzcwNzd9.bgdyMvTvicf5FvAlQXN-311k0WTZg0-72wqR4hb66dQ"
        ))
        .withBody(coeRequestBody)
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      when(mockOutboundConnector.postMessage(xmlRequestCaptor)(*)).thenReturn(successful(SendFail(INTERNAL_SERVER_ERROR)))

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      verify(mockOutboundConnector).postMessage(*)(*)
      xmlRequestCaptor hasCaptured coeRequestBody
    }
  }
}

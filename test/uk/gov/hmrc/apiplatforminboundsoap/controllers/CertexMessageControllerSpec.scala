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
import scala.xml.Elem

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{PassThroughModeAction, VerifyJwtTokenAction}
import uk.gov.hmrc.apiplatforminboundsoap.controllers.certex.CertexMessageController
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendNotAttempted, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.InboundCertexMessageService

class CertexMessageControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {

    val app: Application      = new GuiceApplicationBuilder()
      .configure("passThroughEnabled.CERTEX" -> "false", "microservice.services.certex-service.authToken" -> "auth")
      .build()
    val xRequestIdHeaderValue = randomUUID.toString()

    val commonHeaders = Headers(
      "Host"         -> "localhost",
      "x-request-id" -> xRequestIdHeaderValue,
      "Content-Type" -> "text/xml"
    )

    val headersWithValidBearerToken = commonHeaders.add(
      "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwNDM1NzAwNDUsImlzcyI6ImMzYTlhMTAxLTkzN2ItNDdjMS1iYzM1LWJkYjI0YjEyZTRlNSJ9.00ASmOrt3Ze6DNNGYhWLXWRWWO2gvPjC15G2K5D8fXU"
    )

    private val passThroughModeAction = app.injector.instanceOf[PassThroughModeAction]
    private val verifyJwtTokenAction  = app.injector.instanceOf[VerifyJwtTokenAction]
    val mockService                   = mock[InboundCertexMessageService]

    val controller                     =
      new CertexMessageController(Helpers.stubControllerComponents(), passThroughModeAction, verifyJwtTokenAction, mockService)
    val fakeRequest                    = FakeRequest("POST", "/certex/inbound").withHeaders(headersWithValidBearerToken)
    val fakeRequestPartlyUpperCasePath = FakeRequest("POST", "/CERTEX/inbound").withHeaders(headersWithValidBearerToken)
  }

  "POST Certex message endpoint" should {
    "return 200 for all lower case path" in new Setup {
      val requestBody: Elem = <xml>foobar</xml>
      when(mockService.processInboundMessage(*)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
    }

    "return 200 for part upper case path" in new Setup {
      val requestBody: Elem = <xml>foobar</xml>
      when(mockService.processInboundMessage(*)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequestPartlyUpperCasePath.withBody(requestBody))

      status(result) shouldBe OK
    }

    "return error when unsuccessful with failure in connector sending" in new Setup {
      val requestBody: Elem = <xml>foobar</xml>
      when(mockService.processInboundMessage(*)(*)).thenReturn(successful(SendFailExternal("some error", SERVICE_UNAVAILABLE)))

      val result = controller.message()(fakeRequestPartlyUpperCasePath.withBody(requestBody))

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "error").as[String] shouldBe "some error"
    }

    "return error when send not attempted due to detected error in message format" in new Setup {
      val requestBody: Elem = <xml>foobar</xml>
      when(mockService.processInboundMessage(*)(*)).thenReturn(successful(SendNotAttempted("problem")))

      val result = controller.message()(fakeRequestPartlyUpperCasePath.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] shouldBe "problem"
    }
  }
}

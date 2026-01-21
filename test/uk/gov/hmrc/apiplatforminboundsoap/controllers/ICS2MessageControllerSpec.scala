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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.xml.Elem

import org.mockito.captor.{ArgCaptor, Captor}
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

import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{PassThroughModeAction, SoapMessageValidateAction, VerifyJwtTokenAction}
import uk.gov.hmrc.apiplatforminboundsoap.controllers.ics2.ICS2MessageController
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.InboundIcs2MessageService

class ICS2MessageControllerSpec extends AnyWordSpec with SoapMessageTest with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {

    val app: Application           = new GuiceApplicationBuilder()
      .configure("passThroughEnabled.ICS2" -> "false")
      .build()
    val incomingMessageServiceMock = mock[InboundIcs2MessageService]

    val commonHeaders = Headers(
      "Host"              -> "localhost",
      "http_x_request_id" -> xRequestIdHeaderValue,
      "Content-Type"      -> "text/xml"
    )

    val headersWithValidBearerToken = commonHeaders.add(
      "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwNDM1NzAwNDUsImlzcyI6ImMzYTlhMTAxLTkzN2ItNDdjMS1iYzM1LWJkYjI0YjEyZTRlNSJ9.00ASmOrt3Ze6DNNGYhWLXWRWWO2gvPjC15G2K5D8fXU"
    )

    val headersWithExpiredBearerToken = commonHeaders.add(
      "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE0MTI0MTgwNDUsImlzcyI6ImMzYTlhMTAxLTkzN2ItNDdjMS1iYzM1LWJkYjI0YjEyZTRlNSJ9.VJSs1FfegklhoX_2d2s-uFMYhx2FpzX8pnSvQ1NpdLU"
    )

    val headersWithInvalidIssuerBearerToken = commonHeaders.add(
      "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwNDM1NzAwNDUsImlzcyI6ImFueSBvbGQgY3JhcCJ9.ANiEhrg1ZDCXA5axh4G2RXpyZwGuX7_AU1V3FJdX5DU"
    )
    private val passThroughModeAction       = app.injector.instanceOf[PassThroughModeAction]
    private val verifyJwtTokenAction        = app.injector.instanceOf[VerifyJwtTokenAction]
    private val soapMessageValidateAction   = app.injector.instanceOf[SoapMessageValidateAction]

    val controller  =
      new ICS2MessageController(Helpers.stubControllerComponents(), passThroughModeAction, verifyJwtTokenAction, soapMessageValidateAction, incomingMessageServiceMock)
    val fakeRequest = FakeRequest("POST", "/ics2/NESControlBASV2").withHeaders(headersWithValidBearerToken)
  }

  "POST CCN2 message endpoint " should {
    "return 200 when successful for a message with embedded attached file" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val isTestCaptor: Captor[Boolean]  = ArgCaptor[Boolean]
      val requestBody: Elem              = readFromFile("ie4r02-v2-one-binary-attachment.xml")
      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
      verify(incomingMessageServiceMock).processInboundMessage(*, *)(*)
      xmlRequestCaptor hasCaptured requestBody
      isTestCaptor hasCaptured false
    }

    "return 200 when successful for a message with attached file as URI" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val isTestCaptor: Captor[Boolean]  = ArgCaptor[Boolean]
      val requestBody: Elem              = readFromFile("ie4r02-v2-uri-instead-of-includedBinaryObject-element.xml")
      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
      verify(incomingMessageServiceMock).processInboundMessage(*, *)(*)
      xmlRequestCaptor hasCaptured requestBody
      isTestCaptor hasCaptured false
    }

    "return 200 when successful for a message with binary file and binary attachment" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val isTestCaptor: Captor[Boolean]  = ArgCaptor[Boolean]
      val requestBody: Elem              = readFromFile("uriAndBinaryObject/ie4r02-v2-both-binaryFile-and-binaryAttachment-elements-files-inline.xml")
      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
      verify(incomingMessageServiceMock).processInboundMessage(*, *)(*)
      xmlRequestCaptor hasCaptured requestBody
      isTestCaptor hasCaptured false
    }

    "return 200 when successful for a message with a binary file and 2 binary attachments" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val isTestCaptor: Captor[Boolean]  = ArgCaptor[Boolean]
      val requestBody: Elem              = readFromFile("ie4r02-v2-one-binaryFile-and-two-binaryAttachment-elements-files-inline.xml")
      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
      verify(incomingMessageServiceMock).processInboundMessage(*, *)(*)
      xmlRequestCaptor hasCaptured requestBody
      isTestCaptor hasCaptured false
    }

    "return 200 when successful for a message with no attached file" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val isTestCaptor: Captor[Boolean]  = ArgCaptor[Boolean]
      val requestBody: Elem              = readFromFile("ie4n09-v2.xml")
      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
      contentType(result) shouldBe Some("application/soap+xml")
      verify(incomingMessageServiceMock).processInboundMessage(*, *)(*)
      xmlRequestCaptor hasCaptured requestBody
      isTestCaptor hasCaptured false
    }

    "return response code it received when not successful" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val isTestCaptor: Captor[Boolean]  = ArgCaptor[Boolean]
      val requestBody: Elem              = readFromFile("ie4r02-v2-one-binary-attachment.xml")
      val expectedStatus                 = PRECONDITION_FAILED
      val expectedSoapMessage            = expectedSoapResponse("some error", expectedStatus)

      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor, isTestCaptor)(*)).thenReturn(successful(SendFailExternal("some error", PRECONDITION_FAILED)))

      val result = controller.message()(fakeRequest.withBody(requestBody))

      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
      status(result) shouldBe expectedStatus
      verify(incomingMessageServiceMock).processInboundMessage(*, *)(*)
      xmlRequestCaptor hasCaptured requestBody
      isTestCaptor hasCaptured false
    }

    "return 400 when MIME element is too long and referralRequestReference is too long" in new Setup {
      val requestBody: Elem   = readFromFile("ie4r02-v2-too-long--mime-and-referralRequest-Reference-elements.xml")
      val expectedSoapMessage = expectedSoapResponse("Value of element referralRequestReference is too long\nValue of element MIME is too long")

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST

      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
      verifyZeroInteractions(incomingMessageServiceMock)
    }

    "return 400 when includedBinaryObject element is blank" in new Setup {
      val requestBody: Elem   = readFromFile("uriAndBinaryObject/ie4r02-v2-blank-includedBinaryObject-element.xml")
      val expectedSoapMessage = expectedSoapResponse("Value of element includedBinaryObject is not valid base 64 data")

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
      verifyZeroInteractions(incomingMessageServiceMock)
    }

    "return 400 when includedBinaryObject element is not base 64 data" in new Setup {
      val requestBody: Elem   = readFromFile("uriAndBinaryObject/ie4r02-v2-includedBinaryObject-element-not-base64.xml")
      val expectedSoapMessage = expectedSoapResponse("Value of element includedBinaryObject is not valid base 64 data")

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
      verifyZeroInteractions(incomingMessageServiceMock)
    }

    "return 400 when uri element is too short" in new Setup {
      val requestBody: Elem   = readFromFile("uriAndBinaryObject/ie4r02-v2-too-short-uri-element.xml")
      val expectedSoapMessage = expectedSoapResponse("Value of element URI is too short")

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
      verifyZeroInteractions(incomingMessageServiceMock)
    }

    "return 400 when action element is missing" in new Setup {
      val requestBody: Elem   = readFromFile("action/ie4r02-v2-missing-action-element.xml")
      val expectedSoapMessage = expectedSoapResponse("Element SOAP Header Action is missing")

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
      verifyZeroInteractions(incomingMessageServiceMock)
    }
  }
}

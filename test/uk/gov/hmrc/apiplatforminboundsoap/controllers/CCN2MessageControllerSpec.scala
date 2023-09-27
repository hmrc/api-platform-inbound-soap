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

import org.mockito.captor.{ArgCaptor, Captor}
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.SoapMessageValidateAction
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.InboundMessageService
import uk.gov.hmrc.apiplatformoutboundsoap.controllers.actionBuilders.VerifyJwtTokenAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.io.Source
import scala.xml.{Elem, XML}

class CCN2MessageControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  trait Setup {
    val incomingMessageServiceMock        = mock[InboundMessageService]
    val xRequestIdHeaderValue             = randomUUID.toString()

    val headers                           = Headers(
      "Host"          -> "localhost",
      "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwMDc5NzcwNzd9.bgdyMvTvicf5FvAlQXN-311k0WTZg0-72wqR4hb66dQ",
      "x-request-id"  -> xRequestIdHeaderValue,
      "Content-Type"  -> "text/xml"
    )
    private val verifyJwtTokenAction      = app.injector.instanceOf[VerifyJwtTokenAction]
    private val soapMessageValidateAction = app.injector.instanceOf[SoapMessageValidateAction]
    val controller                        = new CCN2MessageController(Helpers.stubControllerComponents(), verifyJwtTokenAction, soapMessageValidateAction, incomingMessageServiceMock)
    val fakeRequest = FakeRequest("POST", "/ics2/NESControlBASV2").withHeaders(headers)
    def readFromFile(fileName: String) = {
      XML.load(Source.fromResource(fileName).bufferedReader())
    }
  }
  private def getExpectedSoapFault(statusCode: Int, reason: String, requestId: String) = {
    s"""<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
       |    <soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
       |    <soap:Body>
       |        <soap:Fault>
       |            <soap:Code>
       |                <soap:Value>soap:$statusCode</soap:Value>
       |            </soap:Code>
       |            <soap:Reason>
       |                <soap:Text xml:lang="en">$reason</soap:Text>
       |            </soap:Reason>
       |            <soap:Node>public-soap-proxy</soap:Node>
       |            <soap:Detail>
       |                <RequestId>$requestId</RequestId>
       |            </soap:Detail>
       |        </soap:Fault>
       |    </soap:Body>
       |</soap:Envelope>""".stripMargin
  }

  "POST CCN2 message endpoint " should {
    "return 200 when successful" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val requestBody: Elem              = readFromFile("ie4s03-v2.xml")
      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor)).thenReturn(successful(SendSuccess))

      val result = controller.message("NESControlBASV2")(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
      verify(incomingMessageServiceMock).processInboundMessage(*)
      xmlRequestCaptor hasCaptured requestBody
    }

    "return response code it received when not successful" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val requestBody: Elem              = readFromFile("ie4s03-v2.xml")

      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor)).thenReturn(successful(SendFail(PRECONDITION_FAILED)))

      val result = controller.message("NESControlBASV2")(fakeRequest.withBody(requestBody))

      status(result) shouldBe PRECONDITION_FAILED
      verify(incomingMessageServiceMock).processInboundMessage(*)
      xmlRequestCaptor hasCaptured requestBody
    }

    "return 400 when description element is missing" in new Setup {
      val requestBody: Elem = readFromFile("ie4r02-v2-missing-description-element.xml")

      val result = controller.message("NESControlBASV2")(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe getExpectedSoapFault(400, "Argument description too short", xRequestIdHeaderValue)
      verifyZeroInteractions(incomingMessageServiceMock)
    }

    "return 400 when description element is blank" in new Setup {
      val requestBody: Elem = readFromFile("ie4r02-v2-blank-description-element.xml")

      val result = controller.message("NESControlBASV2")(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe getExpectedSoapFault(400, "Argument description too short", xRequestIdHeaderValue)
      verifyZeroInteractions(incomingMessageServiceMock)
    }

    "return 400 when description element is too long" in new Setup {
      val requestBody: Elem = readFromFile("ie4r02-v2-too-long-description-element.xml")

      val result = controller.message("NESControlBASV2")(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe getExpectedSoapFault(400, "Argument description too long", xRequestIdHeaderValue)
      verifyZeroInteractions(incomingMessageServiceMock)
    }
  }
}

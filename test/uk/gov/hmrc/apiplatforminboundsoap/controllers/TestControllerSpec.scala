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
import scala.io.Source
import scala.xml.{Elem, XML}

import org.mockito.captor.ArgCaptor
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.mvc.Headers
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{SoapMessageValidateAction, VerifyJwtTokenAction}
import uk.gov.hmrc.apiplatforminboundsoap.controllers.testmessage.TestController
import uk.gov.hmrc.apiplatforminboundsoap.mocks.services.Ics2MessageServiceMockModule

class TestControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup extends Ics2MessageServiceMockModule {
    val xmlRequestCaptor      = ArgCaptor[Elem]
    val isTestCaptor          = ArgCaptor[Boolean]
    val xRequestIdHeaderValue = randomUUID.toString

    val headers                           = Headers(
      "Host"              -> "localhost",
      "Authorization"     -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwNDM1NzAwNDUsImlzcyI6ImMzYTlhMTAxLTkzN2ItNDdjMS1iYzM1LWJkYjI0YjEyZTRlNSJ9.00ASmOrt3Ze6DNNGYhWLXWRWWO2gvPjC15G2K5D8fXU",
      "http_x_request_id" -> xRequestIdHeaderValue,
      "Content-Type"      -> "text/xml"
    )
    private val verifyJwtTokenAction      = app.injector.instanceOf[VerifyJwtTokenAction]
    private val soapMessageValidateAction = app.injector.instanceOf[SoapMessageValidateAction]

    val controller  = new TestController(
      Helpers.stubControllerComponents(),
      verifyJwtTokenAction,
      soapMessageValidateAction,
      Ics2MessageServiceMock.theMock
    )
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
       |            <soap:Node>api-platform-inbound-soap</soap:Node>
       |            <soap:Detail>
       |                <RequestId>$requestId</RequestId>
       |            </soap:Detail>
       |        </soap:Fault>
       |    </soap:Body>
       |</soap:Envelope>""".stripMargin
  }

  "POST test message endpoint " should {
    "return 200 when successful for a message with embedded attached file" in new Setup {
      val requestBody: Elem = readFromFile("ie4r02-v2-one-binary-attachment.xml")
      Ics2MessageServiceMock.ProcessInboundMessage.succeedsForTestMessage("some body")

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
      Ics2MessageServiceMock.ProcessInboundMessage.verifyCalledWithBodyForTestMessage(requestBody, true)
    }

    "return response code it received when not successful" in new Setup {

      val requestBody: Elem = readFromFile("ie4r02-v2-one-binary-attachment.xml")

      Ics2MessageServiceMock.ProcessInboundMessage.failsInSendingTestMessage("some error", PRECONDITION_FAILED)

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe PRECONDITION_FAILED
      Ics2MessageServiceMock.ProcessInboundMessage.verifyCalledWithBodyForTestMessage(requestBody, true)
    }

    "return 400 when action element is missing" in new Setup {
      val requestBody: Elem = readFromFile("action/ie4r02-v2-missing-action-element.xml")

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe getExpectedSoapFault(400, "Element SOAP Header Action is missing", xRequestIdHeaderValue)
      Ics2MessageServiceMock.ProcessInboundMessage.verifyNotCalled()
    }
  }
}

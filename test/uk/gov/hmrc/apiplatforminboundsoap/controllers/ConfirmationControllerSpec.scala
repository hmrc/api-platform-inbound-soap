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
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.DiffBuilder.compare
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors.byName

import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.ApiPlatformOutboundSoapConnector
import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{AcknowledgementMessageValidateAction, PassThroughModeAction, VerifyJwtTokenAction}
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendSuccess}

class ConfirmationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier            = HeaderCarrier()
  implicit val mat: Materializer            = app.injector.instanceOf[Materializer]

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .configure("passThroughEnabled" -> "false")
    .build()

  trait Setup {
    val xRequestIdHeaderValue = randomUUID.toString

    val headers = Headers(
      "Host"         -> "localhost",
      "x-request-id" -> xRequestIdHeaderValue,
      "Content-Type" -> "text/xml"
    )

    val validBearerToken =
      "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwNDM1NzAwNDUsImlzcyI6ImMzYTlhMTAxLTkzN2ItNDdjMS1iYzM1LWJkYjI0YjEyZTRlNSJ9.00ASmOrt3Ze6DNNGYhWLXWRWWO2gvPjC15G2K5D8fXU"

    def readFromFile(fileName: String) = {
      XML.load(Source.fromResource(fileName).bufferedReader())
    }

    val codRequestBody: Elem = readFromFile("acknowledgement/requests/cod_request.xml")
    val coeRequestBody: Elem = readFromFile("acknowledgement/requests/coe_request.xml")

    private val verifyJwtTokenAction  = fakeApplication.injector.instanceOf[VerifyJwtTokenAction]
    private val messageValidateAction = fakeApplication.injector.instanceOf[AcknowledgementMessageValidateAction]
    private val passThroughModeAction = fakeApplication.injector.instanceOf[PassThroughModeAction]
    val mockOutboundConnector         = mock[ApiPlatformOutboundSoapConnector]
    val controller                    = new ConfirmationController(mockOutboundConnector, Helpers.stubControllerComponents(), passThroughModeAction, verifyJwtTokenAction, messageValidateAction)
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

  "POST acknowledgement endpoint with invalid request body" should {
    "return 400 for missing Action element" in new Setup {
      val codRequestBodyMissingAction: Elem = readFromFile("acknowledgement/requests/cod_request_missing_action.xml")
      val expectedSoapMessage               =
        s"""<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
           |<soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
           |<soap:Body>
           |<soap:Fault>
           |<soap:Code>
           |<soap:Value>soap:400</soap:Value>
           |</soap:Code>
           |<soap:Reason>
           |<soap:Text xml:lang="en">Element SOAP Header Action is missing</soap:Text>
           |</soap:Reason>
           |<soap:Node>public-soap-proxy</soap:Node>
           |<soap:Detail>
           |<RequestId>$xRequestIdHeaderValue</RequestId>
           |</soap:Detail>
           |</soap:Fault>
           |</soap:Body>
           |</soap:Envelope>
           |""".stripMargin
      val fakeRequest                       = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(validBearerToken, "Content-Type" -> "application/soap+xml"))
        .withBody(codRequestBodyMissingAction)

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
    }

    "return 400 for empty Action element" in new Setup {
      val codRequestBodyMissingAction: Elem = readFromFile("acknowledgement/requests/cod_request_empty_action.xml")
      val expectedSoapMessage               =
        s"""<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
           |<soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
           |<soap:Body>
           |<soap:Fault>
           |<soap:Code>
           |<soap:Value>soap:400</soap:Value>
           |</soap:Code>
           |<soap:Reason>
           |<soap:Text xml:lang="en">SOAP Header Action should contain / character but does not
           |Value of element SOAP Header Action is too short</soap:Text>
           |</soap:Reason>
           |<soap:Node>public-soap-proxy</soap:Node>
           |<soap:Detail>
           |<RequestId>$xRequestIdHeaderValue</RequestId>
           |</soap:Detail>
           |</soap:Fault>
           |</soap:Body>
           |</soap:Envelope>
           |""".stripMargin
      val fakeRequest                       = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(validBearerToken, "Content-Type" -> "application/soap+xml"))
        .withBody(codRequestBodyMissingAction)

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
    }

    "return 400 for empty MessageID element" in new Setup {
      val codRequestBodyMissingAction: Elem = readFromFile("acknowledgement/requests/cod_request_empty_messageid.xml")
      val expectedSoapMessage               =
        s"""<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
           |<soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
           |<soap:Body>
           |<soap:Fault>
           |<soap:Code>
           |<soap:Value>soap:400</soap:Value>
           |</soap:Code>
           |<soap:Reason>
           |<soap:Text xml:lang="en">Value of element SOAP Header MessageID is too short</soap:Text>
           |</soap:Reason>
           |<soap:Node>public-soap-proxy</soap:Node>
           |<soap:Detail>
           |<RequestId>$xRequestIdHeaderValue</RequestId>
           |</soap:Detail>
           |</soap:Fault>
           |</soap:Body>
           |</soap:Envelope>
           |""".stripMargin
      val fakeRequest                       = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(validBearerToken, "Content-Type" -> "application/soap+xml"))
        .withBody(codRequestBodyMissingAction)

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      getXmlDiff(contentAsString(result), expectedSoapMessage).build().hasDifferences shouldBe false
    }
  }

  "POST acknowledgement endpoint with valid authorisation header and COD request body" should {
    "return 200" in new Setup {
      val fakeRequest                    = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(validBearerToken))
        .withBody(codRequestBody)
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      when(mockOutboundConnector.postMessage(xmlRequestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.OK
      verify(mockOutboundConnector).postMessage(*)(*)
      xmlRequestCaptor hasCaptured codRequestBody
    }
  }

  "POST acknowledgement endpoint with valid authorisation header and COE request body" should {
    "return 200" in new Setup {
      val fakeRequest                    = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(validBearerToken))
        .withBody(coeRequestBody)
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      when(mockOutboundConnector.postMessage(xmlRequestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.OK
      verify(mockOutboundConnector).postMessage(*)(*)
      xmlRequestCaptor hasCaptured coeRequestBody
    }
  }

  "POST acknowledgement endpoint with valid authorisation header and COE request body but outbound connector returns 500" should {
    "return 200" in new Setup {
      val fakeRequest                    = FakeRequest("POST", "/ccn2/acknowledgementV2")
        .withHeaders(headers.add(validBearerToken))
        .withBody(coeRequestBody)
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      when(mockOutboundConnector.postMessage(xmlRequestCaptor)(*)).thenReturn(successful(SendFailExternal(INTERNAL_SERVER_ERROR)))

      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      verify(mockOutboundConnector).postMessage(*)(*)
      xmlRequestCaptor hasCaptured coeRequestBody
    }
  }

  private def getXmlDiff(actual: String, expected: String): DiffBuilder = {
    compare(expected)
      .withTest(actual)
      .withNodeMatcher(new DefaultNodeMatcher(byName))
      .checkForIdentical
      .ignoreComments
      .ignoreWhitespace
  }
}

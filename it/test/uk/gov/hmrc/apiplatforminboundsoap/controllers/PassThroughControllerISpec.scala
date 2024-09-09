/*
 * Copyright 2024 HM Revenue & Customs
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

import scala.io.Source
import scala.xml.{Elem, XML}

import com.github.tomakehurst.wiremock.http.Fault
import org.apache.pekko.stream.Materializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Headers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.test.{ExternalWireMockSupport, HttpClientV2Support}

import uk.gov.hmrc.apiplatforminboundsoap.support.ExternalServiceStub

class PassThroughControllerISpec extends AnyWordSpecLike with Matchers with HttpClientV2Support with ExternalWireMockSupport with GuiceOneAppPerSuite with ExternalServiceStub {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"  -> false,
      "auditing.enabled" -> false,
      "passThroughHost"  -> externalWireMockHost,
      "passThroughPort"  -> externalWireMockPort
    ).build()
  implicit val mat: Materializer              = app.injector.instanceOf[Materializer]

  val path        = "/ics2/NESReferralBASV2"
  val fakeRequest = FakeRequest("POST", path)

  val underTest: PassThroughController = app.injector.instanceOf[PassThroughController]
  "message" should {
    "forward an XML message" in {
      val expectedStatus  = Status.ACCEPTED
      val expectedHeaders = Headers("Authorization" -> "Bearer blah")
      primeStubForSuccess("OK", expectedStatus, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

      val result = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders(expectedHeaders))
      status(result) shouldBe expectedStatus

      verifyRequestBody(payload.toString(), path)
      expectedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
    }

    "forward Authorization header" in {
      val expectedStatus  = Status.ACCEPTED
      val expectedHeaders = Headers("Authorization" -> "Bearer blah")
      primeStubForSuccess("OK", expectedStatus, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

      val result = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders(expectedHeaders))
      status(result) shouldBe expectedStatus

      expectedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
    }

    "reject requests with missing Authorization header" in {
      val expectedStatus = Status.BAD_REQUEST
      val requestHeaders = Headers("Foo" -> "Bar")
      primeStubForSuccess("OK", expectedStatus, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

      val result = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders(requestHeaders))
      status(result) shouldBe expectedStatus

    }

    "forward to a valid URL when path is missing a leading stroke (as the Router does it)" in {
      val pathWithoutLeadingStroke = "ics2/NESReferralBASV2"
      val expectedStatus           = Status.ACCEPTED
      val expectedHeaders          = Headers("Authorization" -> "Bearer blah")
      primeStubForSuccess("OK", expectedStatus, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

      val result = underTest.message(pathWithoutLeadingStroke)(fakeRequest.withXmlBody(payload).withHeaders(expectedHeaders))
      status(result) shouldBe expectedStatus
      expectedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
    }

    "forward an XML message to the right path" in {
      val paths = Table(
        "path",
        "/ics2/NESErrorNotificationBAS",
        "/ics2/NESNotificationBAS",
        "/ics2/NESReferralBAS",
        "/ics2/NESRiskAnalyisBAS",
        "/ics2/NESRiskAnalysisBAS",
        "/ics2/NESControlBASV2",
        "/ics2/NESErrorNotificationBASV2",
        "/ics2/NESNotificationBASV2",
        "/ics2/NESReferralBASV2",
        "/ics2/NESRiskAnalyisBASV2",
        "/ics2/NESRiskAnalysisBASV2"
      )

      val expectedStatus  = Status.ACCEPTED
      val expectedHeaders = Headers("Authorization" -> "Bearer blah")

      forAll(paths) { path: String =>
        val fakeRequest = FakeRequest("POST", "/")
        primeStubForSuccess("OK", expectedStatus, path)

        val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

        val result = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders(expectedHeaders))
        status(result) shouldBe expectedStatus
        expectedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
      }
    }

    "reject an XML message with the wrong Content-Type header" in {
      val expectedStatus = Status.BAD_REQUEST
      primeStubForSuccess("OK", expectedStatus, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

      val result = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders("Content-Type" -> "application/json"))
      status(result) shouldBe expectedStatus
    }

    "reject a JSON message with application/soap+xml Content-Type header" in {
      val expectedStatus  = Status.BAD_REQUEST
      val expectedHeaders = Headers("Authorization" -> "Bearer blah", "Content-Type" -> "application/soap+xml")

      primeStubForSuccess("OK", expectedStatus, path)

      val result = underTest.message(path)(fakeRequest.withJsonBody(Json.toJson(Json.obj("foo" -> "bar")))
        .withHeaders(expectedHeaders))
      status(result) shouldBe expectedStatus
    }

    "handle an server error response from the forward-to service" in {
      val expectedStatus  = Status.INTERNAL_SERVER_ERROR
      val expectedHeaders = Headers("Authorization" -> "Bearer blah")
      primeStubForFault("Something went wrong", Fault.CONNECTION_RESET_BY_PEER, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())
      val result        = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders(expectedHeaders))

      status(result) shouldBe expectedStatus
    }

    "handle an unsuccessful response from the forward-to service" in {
      val expectedStatus  = Status.UNAUTHORIZED
      val expectedHeaders = Headers("Authorization" -> "Bearer blah")
      primeStubForSuccess("Unauthorized", Status.UNAUTHORIZED, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())
      val result        = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders(expectedHeaders))

      status(result) shouldBe expectedStatus
    }

    "return the SOAP fault response payload to the caller" in {
      val expectedStatus  = Status.BAD_REQUEST
      val expectedHeaders = Headers("Authorization" -> "Bearer blah")
      primeStubForSuccess(soapFaultResponse, Status.BAD_REQUEST, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())
      val result        = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders(expectedHeaders))

      status(result) shouldBe expectedStatus
      contentAsString(result) shouldBe (soapFaultResponse)
    }
  }

  val soapFaultResponse = """<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
                            |    <soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
                            |    <soap:Body>
                            |        <soap:Fault>
                            |            <soap:Code>
                            |                <soap:Value>soap:400</soap:Value>
                            |            </soap:Code>
                            |            <soap:Reason>
                            |                <soap:Text xml:lang="en">Some Fault</soap:Text>
                            |            </soap:Reason>
                            |            <soap:Node>public-soap-proxy</soap:Node>
                            |            <soap:Detail>
                            |                <RequestId>abcd1234</RequestId>
                            |            </soap:Detail>
                            |        </soap:Fault>
                            |    </soap:Body>
                            |</soap:Envelope>""".stripMargin
}

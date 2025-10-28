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
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Headers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.test.{ExternalWireMockSupport, HttpClientV2Support}

import uk.gov.hmrc.apiplatforminboundsoap.controllers.ics2.ICS2MessageController
import uk.gov.hmrc.apiplatforminboundsoap.wiremockstubs.ExternalServiceStub

class ICS2ControllerPassThroughISpec extends AnyWordSpecLike with Matchers
    with HttpClientV2Support with ExternalWireMockSupport with GuiceOneAppPerSuite with ExternalServiceStub with TableDrivenPropertyChecks {

  def readFromFile(fileName: String) = {
    XML.load(Source.fromResource(fileName).bufferedReader())
  }

  val codRequestBody: Elem = readFromFile("requests/ie4r02-v2.xml")

  val soapFaultResponse =
    """<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
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

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"         -> false,
      "auditing.enabled"        -> false,
      "passThroughEnabled.ICS2" -> true,
      "passThroughProtocol"     -> "http",
      "passThroughHost"         -> externalWireMockHost,
      "passThroughPort"         -> externalWireMockPort
    ).build()

  implicit val mat: Materializer = fakeApplication().injector.instanceOf[Materializer]

  val path        = "/ics2/NESControlBASV2"
  val fakeRequest = FakeRequest("POST", path)

  val underTest: ICS2MessageController = fakeApplication.injector.instanceOf[ICS2MessageController]
  "message" should {
    "forward an XML message" in {
      val expectedStatus = Status.OK

      val expectedHeaders = Headers("Authorization" -> "Bearer blah", "Content-Type" -> "text/xml; charset=UTF-8")
      primeStubForSuccess("OK", expectedStatus, path)
      val result          = underTest.message()(fakeRequest.withBody(codRequestBody).withHeaders(expectedHeaders))
      status(result) shouldBe expectedStatus

      verifyRequestBody(codRequestBody.toString(), path)
      expectedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
    }

    "handle an unknown path while in pass-through mode" in {
      val expectedStatus         = Status.NOT_FOUND
      val fakeRequestUnknownPath = FakeRequest("POST", "/foo/bar")

      val expectedHeaders = Headers(
        "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJjM2E5YTEwMS05MzdiLTQ3YzEtYmMzNS1iZGIyNGIxMmU0ZTUiLCJleHAiOjIwNTU0MTQ5NzN9.T2tTGStmVttHtj2Hruk5N1yh4AUyPVuy6t5d-gH0tZU",
        "Content-Type"  -> "text/xml; charset=UTF-8"
      )
      primeStubForSuccess("OK", expectedStatus, path)
      val result          = underTest.message()(fakeRequestUnknownPath.withBody(codRequestBody).withHeaders(expectedHeaders))
      status(result) shouldBe expectedStatus
    }

    "forward an XML message when Authorization header missing from request" in {
      val expectedStatus = Status.UNAUTHORIZED

      val receivedHeaders = Headers("Content-Type" -> "text/xml; charset=UTF-8")
      val expectedHeaders = Headers("Content-Type" -> "text/xml; charset=UTF-8")
      primeStubForSuccess(soapFaultResponse, expectedStatus, path)
      val result          = underTest.message()(fakeRequest.withBody(codRequestBody).withHeaders(receivedHeaders))

      status(result) shouldBe expectedStatus
      contentAsString(result) shouldEqual soapFaultResponse
      verifyRequestBody(codRequestBody.toString(), path)
      expectedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
    }

    "maintain the request Content-Type on forwarded message" in {
      // note that text/plain and text/xml, after passing through http-verbs, have a charset added
      val contentTypes = Table(
        ("input", "output"),
        ("application/xml", "application/xml"),
        ("text/plain", "text/plain; charset=UTF-8"),
        ("text/xml", "text/xml; charset=UTF-8")
      )
      forAll(contentTypes) { (c: String, o: String) =>
        val expectedStatus = Status.OK

        val receivedHeaders = Headers("Content-Type" -> c)
        val expectedHeaders = Headers("Content-Type" -> o)
        primeStubForSuccess(soapFaultResponse, expectedStatus, path)
        val result          = underTest.message()(fakeRequest.withBody(codRequestBody).withHeaders(receivedHeaders))

        status(result) shouldBe expectedStatus
        contentAsString(result) shouldEqual soapFaultResponse
        verifyRequestBody(codRequestBody.toString(), path)
        expectedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
      }
    }

    "return error responses to caller" in {
      val expectedStatus = Status.INTERNAL_SERVER_ERROR

      val receivedHeaders = Headers("Content-Type" -> "application/xml")
      val expectedHeaders = Headers("Content-Type" -> "text/xml; charset=UTF-8")
      primeStubForFault("Error", Fault.CONNECTION_RESET_BY_PEER, path)
      val result          = underTest.message()(fakeRequest.withBody(codRequestBody).withHeaders(receivedHeaders))
      status(result) shouldBe expectedStatus

      verifyRequestBody(codRequestBody.toString(), path)
      expectedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
    }

    "reject an non-XML message" in {
      val expectedStatus  = Status.BAD_REQUEST
      val expectedHeaders = Headers("Authorization" -> "Bearer blah", "Content-Type" -> "text/xml; charset=UTF-8")
      val result          = underTest.message()(fakeRequest.withBody("foobar").withHeaders(expectedHeaders))
      status(result) shouldBe expectedStatus
    }
  }

}

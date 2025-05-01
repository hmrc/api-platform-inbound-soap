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

import org.apache.pekko.stream.Materializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Headers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.test.{ExternalWireMockSupport, HttpClientV2Support}

import uk.gov.hmrc.apiplatforminboundsoap.wiremockstubs.ExternalServiceStub

class ConfirmationControllerPassThroughISpec extends AnyWordSpecLike with Matchers
    with HttpClientV2Support with ExternalWireMockSupport with GuiceOneAppPerSuite with ExternalServiceStub {

  def readFromFile(fileName: String) = {
    XML.load(Source.fromResource(fileName).bufferedReader())
  }

  val codRequestBody: Elem = readFromFile("acknowledgement-requests/cod_request.xml")

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"     -> false,
      "auditing.enabled"    -> false,
      "passThroughProtocol" -> "http",
      "passThroughEnabled"  -> true,
      "passThroughHost"     -> externalWireMockHost,
      "passThroughPort"     -> externalWireMockPort
    ).build()
  implicit val mat: Materializer              = app.injector.instanceOf[Materializer]

  val path        = "/ccn2/acknowledgementV2"
  val fakeRequest = FakeRequest("POST", path)

  val underTest: ConfirmationController = app.injector.instanceOf[ConfirmationController]
  "message" should {
    "forward an XML message" in {
      val expectedStatus = Status.OK

      val requestHeaders   = Headers(
        "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJjM2E5YTEwMS05MzdiLTQ3YzEtYmMzNS1iZGIyNGIxMmU0ZTUiLCJleHAiOjIwNTU0MTQ5NzN9.T2tTGStmVttHtj2Hruk5N1yh4AUyPVuy6t5d-gH0tZU",
        "Content-Type"  -> "text/xml; charset=UTF-8"
      )
      val forwardedHeaders = Headers("Content-Type" -> "text/xml; charset=UTF-8")
      primeStubForSuccess("OK", expectedStatus, path)
      val result           = underTest.message()(fakeRequest.withBody(codRequestBody).withHeaders(requestHeaders))
      status(result) shouldBe expectedStatus

      verifyRequestBody(codRequestBody.toString, path)
      forwardedHeaders.headers.foreach(h => verifyHeader(h._1, h._2, path = path))
    }

    "reject an non-XML message" in {
      val expectedStatus  = Status.BAD_REQUEST
      val expectedHeaders = Headers("Authorization" -> "Bearer blah", "Content-Type" -> "text/xml; charset=UTF-8")
      val result          = underTest.message()(fakeRequest.withBody("foobar").withHeaders(expectedHeaders))
      status(result) shouldBe expectedStatus
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

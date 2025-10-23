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

import com.github.tomakehurst.wiremock.client.WireMock.{havingExactly, postRequestedFor, urlPathEqualTo, verify}
import com.github.tomakehurst.wiremock.http.Fault
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

import uk.gov.hmrc.apiplatforminboundsoap.controllers.ics2.ICS2MessageController
import uk.gov.hmrc.apiplatforminboundsoap.wiremockstubs.ExternalServiceStub

class ICS2ControllerISpec extends AnyWordSpecLike with Matchers
    with HttpClientV2Support with ExternalWireMockSupport with GuiceOneAppPerSuite with ExternalServiceStub {

  def readFromFile(fileName: String) = {
    XML.load(Source.fromResource(fileName).bufferedReader())
  }

  val ics2RequestBody: Elem = readFromFile("requests/ie4r02-v2.xml")
  val forwardedBody: Elem   = readFromFile("requests/post-sdes-processing/ie4r02-v2-one-binary-attachment.xml")

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"                                                           -> false,
      "auditing.enabled"                                                          -> false,
      "passThroughEnabled.ICS2"                                                   -> false,
      "microservice.services.secure-data-exchange-proxy.ics2.encodeSdesReference" -> false,
      "microservice.services.import-control-inbound-soap.host"                    -> externalWireMockHost,
      "microservice.services.import-control-inbound-soap.port"                    -> externalWireMockPort,
      "microservice.services.secure-data-exchange-proxy.host"                     -> externalWireMockHost,
      "microservice.services.secure-data-exchange-proxy.port"                     -> externalWireMockPort
    ).build()
  implicit val mat: Materializer            = fakeApplication().injector.instanceOf[Materializer]

  val forwardRequestPath = "/import-control-inbound-soap"
  val receiveRequestPath = "/ics2/NESRiskAnalysisBAS"
  val sdesPath           = "/upload-attachment"
  val fakeRequest        = FakeRequest("POST", receiveRequestPath)

  val expectedRequestHeaders = Headers(
    "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJjM2E5YTEwMS05MzdiLTQ3YzEtYmMzNS1iZGIyNGIxMmU0ZTUiLCJleHAiOjIwNTU0MTQ5NzN9.T2tTGStmVttHtj2Hruk5N1yh4AUyPVuy6t5d-gH0tZU",
    "Content-Type"  -> "application/xml"
  )
  val expectedSdesStatus     = Status.ACCEPTED

  val underTest: ICS2MessageController = fakeApplication().injector.instanceOf[ICS2MessageController]
  "message" should {
    "forward an XML message" in {
      val expectedRequestStatus = Status.OK

      primeStubForSuccess("OK", expectedRequestStatus, forwardRequestPath)
      primeStubForSuccess("some-uuid-like-string", expectedSdesStatus, sdesPath)
      val result = underTest.message()(fakeRequest.withBody(ics2RequestBody).withHeaders(expectedRequestHeaders))
      status(result) shouldBe expectedRequestStatus

      verifyRequestBody(forwardedBody.toString, forwardRequestPath)
      verifyHeaderAbsent("Authorization", forwardRequestPath)
      verify(postRequestedFor(urlPathEqualTo(forwardRequestPath)).withHeader("x-files-included", havingExactly("true")))
    }

    "return downstream error responses to caller" in {
      val expectedStatus = Status.INTERNAL_SERVER_ERROR

      primeStubForFault("Error", Fault.CONNECTION_RESET_BY_PEER, forwardRequestPath)
      primeStubForSuccess("some-uuid-like-string", expectedSdesStatus, sdesPath)
      val result = underTest.message()(fakeRequest.withBody(ics2RequestBody).withHeaders(expectedRequestHeaders))
      status(result) shouldBe expectedStatus

      verifyRequestBody(forwardedBody.toString, forwardRequestPath)
    }

    "reject an non-XML message" in {
      val expectedStatus = Status.BAD_REQUEST
      val result         = underTest.message()(fakeRequest.withBody("foobar").withHeaders(expectedRequestHeaders))
      status(result) shouldBe expectedStatus
    }
  }
}

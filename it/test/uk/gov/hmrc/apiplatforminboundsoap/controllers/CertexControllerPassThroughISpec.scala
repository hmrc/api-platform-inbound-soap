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

import uk.gov.hmrc.apiplatforminboundsoap.controllers.certex.CertexMessageController
import uk.gov.hmrc.apiplatforminboundsoap.wiremockstubs.ExternalServiceStub

class CertexControllerPassThroughISpec extends AnyWordSpecLike with Matchers
    with HttpClientV2Support with ExternalWireMockSupport with GuiceOneAppPerSuite with ExternalServiceStub {

  def readFromFile(fileName: String) = {
    XML.load(Source.fromResource(fileName).bufferedReader())
  }

  val certexRequestBody: Elem = readFromFile("requests/certex/certex-request-no-attachment.xml")

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"           -> false,
      "auditing.enabled"          -> false,
      "passThroughEnabled.CERTEX" -> true,
      "passThroughProtocol"       -> "http",
      "passThroughHost"           -> externalWireMockHost,
      "passThroughPort"           -> externalWireMockPort
    ).build()

  implicit val mat: Materializer = fakeApplication().injector.instanceOf[Materializer]

  val forwardRequestPath = "/CERTEX/inbound"
  val receiveRequestPath = "/CERTEX/inbound"
  val fakeRequest        = FakeRequest("POST", receiveRequestPath)

  private val authBearerJwt =
    "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJjM2E5YTEwMS05MzdiLTQ3YzEtYmMzNS1iZGIyNGIxMmU0ZTUiLCJleHAiOjIwNTU0MTQ5NzN9.T2tTGStmVttHtj2Hruk5N1yh4AUyPVuy6t5d-gH0tZU"

  val expectedRequestHeaders = Headers(
    "Authorization"    -> authBearerJwt,
    "Content-Type"     -> "application/xml",
    "Arbitrary-Header" -> "foobar"
  )
  val expectedSdesStatus     = Status.ACCEPTED

  val underTest: CertexMessageController = fakeApplication().injector.instanceOf[CertexMessageController]
  "message" should {
    "forward an XML message" in {
      val expectedRequestStatus = Status.OK
      primeStubForSuccess("OK", expectedRequestStatus, forwardRequestPath)
      val result                = underTest.message()(fakeRequest.withBody(certexRequestBody).withHeaders(expectedRequestHeaders))
      status(result) shouldBe expectedRequestStatus

      verifyRequestBody(certexRequestBody.toString, forwardRequestPath)
      verify(postRequestedFor(urlPathEqualTo(forwardRequestPath)).withHeader(
        "Authorization",
        havingExactly(authBearerJwt)
      ))
      verify(postRequestedFor(urlPathEqualTo(forwardRequestPath)).withHeader("Content-Type", havingExactly("application/xml")))
      verify(postRequestedFor(urlPathEqualTo(forwardRequestPath)).withHeader("Arbitrary-Header", havingExactly("foobar")))
    }

    "return downstream error responses to caller" in {
      val expectedStatus = Status.INTERNAL_SERVER_ERROR

      primeStubForFault("Error", Fault.CONNECTION_RESET_BY_PEER, forwardRequestPath)
      val result = underTest.message()(fakeRequest.withBody(certexRequestBody).withHeaders(expectedRequestHeaders))
      status(result) shouldBe expectedStatus

      verifyRequestBody(certexRequestBody.toString, forwardRequestPath)
      verify(postRequestedFor(urlPathEqualTo(forwardRequestPath)).withHeader("Content-Type", havingExactly("application/xml")))
    }

    "reject an non-XML message" in {
      val expectedStatus = Status.BAD_REQUEST
      val result         = underTest.message()(fakeRequest.withBody("foobar").withHeaders(expectedRequestHeaders))
      status(result) shouldBe expectedStatus
    }
  }
}

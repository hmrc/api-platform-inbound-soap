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

package uk.gov.hmrc.apiplatforminboundsoap.connectors

import java.util.UUID.randomUUID
import scala.io.Source
import scala.xml.{Elem, XML}

import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.ExternalWireMockSupport

import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendResult, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.wiremockstubs.ApiPlatformOutboundSoapStub

class ImportControlInboundSoapConnectorISpec extends AnyWordSpec with Matchers
    with GuiceOneAppPerSuite with ExternalWireMockSupport with ApiPlatformOutboundSoapStub {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"                                        -> false,
      "auditing.enabled"                                       -> false,
      "testForwardMessageUrl"                                  -> s"$externalWireMockUrl/test-only",
      "microservice.services.import-control-inbound-soap.host" -> externalWireMockHost,
      "microservice.services.import-control-inbound-soap.port" -> externalWireMockPort
    ).build()

  trait Setup {
    val headers: Seq[(String, String)]               = List("Authorization" -> "Bearer value")
    val underTest: ImportControlInboundSoapConnector = app.injector.instanceOf[ImportControlInboundSoapConnector]
    val forwardPath: String                          = "/import-control-inbound-soap"
    val requestBody: Elem                            = <xml>foobar</xml>
    val xRequestIdHeaderValue                        = randomUUID.toString

    val faultResponse = getExpectedSoapFault(
      400,
      "Value of element referralRequestReference is too long\nValue of element MIME is too long",
      xRequestIdHeaderValue
    )

    def readFromFile(fileName: String) = {
      XML.load(Source.fromResource(fileName).bufferedReader())
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
  }

  "postMessage" should {
    "return successful statuses returned by the internal service" in new Setup {
      val expectedStatus: Int = OK
      primeStubForSuccess(requestBody, expectedStatus, forwardPath)

      val result: SendResult = await(underTest.postMessage(requestBody, headers, isTest = false))

      result shouldBe SendSuccess(OK)
      verifyRequestBody(requestBody, forwardPath)
      verifyHeader(headers.head._1, headers.head._2, forwardPath)
    }

    "return error statuses returned by the internal service" in new Setup {
      val expectedStatus: Int = INTERNAL_SERVER_ERROR
      primeStubForSuccess(requestBody, expectedStatus, forwardPath)

      val result: SendResult = await(underTest.postMessage(requestBody, headers, isTest = false))

      result shouldBe SendFailExternal(expectedStatus)
      verifyHeader(headers.head._1, headers.head._2, forwardPath)
    }

    "return error status when soap fault is returned by the internal service" in new Setup {
      Seq(Fault.CONNECTION_RESET_BY_PEER, Fault.EMPTY_RESPONSE, Fault.MALFORMED_RESPONSE_CHUNK, Fault.RANDOM_DATA_THEN_CLOSE) foreach { fault =>
        primeStubForFault(requestBody, faultResponse, fault, forwardPath)

        val result: SendResult = await(underTest.postMessage(requestBody, headers, isTest = false))

        result shouldBe SendFailExternal(INTERNAL_SERVER_ERROR)
        verifyHeader(headers.head._1, headers.head._2, forwardPath)
      }
    }

    "send the given message to the test service if so configured" in new Setup {
      val path = "/test-only"
      primeStubForSuccess(requestBody, OK, path)

      val result: SendResult = await(underTest.postMessage(requestBody, headers, isTest = true))

      result shouldBe SendSuccess(OK)
      verifyRequestBody(requestBody, path)
      verifyHeader(headers.head._1, headers.head._2, path)

    }

    "send the given message to the internal service" in new Setup {
      primeStubForSuccess(requestBody, OK, forwardPath)

      val result: SendResult = await(underTest.postMessage(requestBody, headers, isTest = false))

      result shouldBe SendSuccess(OK)
      verifyRequestBody(requestBody, forwardPath)
      verifyHeader(headers.head._1, headers.head._2, forwardPath)
    }
  }
}

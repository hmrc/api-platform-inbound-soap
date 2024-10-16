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

import java.util.{Base64, UUID}

import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.ExternalWireMockSupport

import uk.gov.hmrc.apiplatforminboundsoap.models.{SdesRequest, SdesSuccess, SendFailExternal, SendResult}
import uk.gov.hmrc.apiplatforminboundsoap.support.ExternalServiceStub

class SdesConnectorISpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with ExternalWireMockSupport with ExternalServiceStub {
  override implicit lazy val app: Application = appBuilder.build()
  implicit val hc: HeaderCarrier              = HeaderCarrier()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"                                       -> false,
        "auditing.enabled"                                      -> false,
        "microservice.services.secure-data-exchange-proxy.host" -> externalWireMockHost,
        "microservice.services.secure-data-exchange-proxy.port" -> externalWireMockPort
      )

  trait Setup {
    val underTest: SdesConnector              = app.injector.instanceOf[SdesConnector]
    val defaultHeaders: Seq[(String, String)] = underTest.requiredHeaders
    val base64EncodedString: String           = Base64.getEncoder.encodeToString(Array[Byte]('a', 'b', 'c'))
    val responseBody: String                  = UUID.randomUUID().toString
    val simpleSdesRequest                     = SdesRequest(headers = List(), metadata = Map.empty, body = base64EncodedString, metadataProperties = Map.empty)
    val path                                  = "/upload-attachment"
  }

  "postMessage" should {

    "return success status and accompanying UUID returned by the SDES" in new Setup {
      val expectedStatus: Int = OK
      primeStubForSuccess(responseBody, expectedStatus, path)

      val result: SendResult = await(underTest.postMessage(simpleSdesRequest))

      result shouldBe SdesSuccess(responseBody)
      verifyRequestBody(simpleSdesRequest.body, path)
      verifyHeadersOnRequest(defaultHeaders, path)
    }

    "send any additional headers" in new Setup {
      val additionalHeaders   = List("x-request-id" -> "abcdefgh1234567890", "any-old-header-name" -> "any-old-header-value")
      val sdesRequest         = SdesRequest(headers = additionalHeaders, metadata = Map.empty, body = base64EncodedString, metadataProperties = Map.empty)
      val expectedStatus: Int = OK
      primeStubForSuccess(responseBody, expectedStatus, path)

      val result: SendResult = await(underTest.postMessage(sdesRequest))

      result shouldBe SdesSuccess(responseBody)
      verifyRequestBody(simpleSdesRequest.body, path)
      verifyHeadersOnRequest(defaultHeaders ++ additionalHeaders, path)
    }

    "send the metadata header" in new Setup {
      val additionalHeaders   = List("x-request-id" -> "abcdefgh1234567890", "any-old-header-name" -> "any-old-header-value")
      val sdesRequest         =
        SdesRequest(headers = additionalHeaders, metadata = Map("foo" -> "bar", "humpty" -> "dumpty"), body = base64EncodedString, metadataProperties = Map.empty)
      val expectedStatus: Int = OK
      primeStubForSuccess(responseBody, expectedStatus, path)

      val result: SendResult = await(underTest.postMessage(sdesRequest))

      result shouldBe SdesSuccess(responseBody)
      verifyRequestBody(simpleSdesRequest.body, path)

      verifyHeadersOnRequest(defaultHeaders ++ additionalHeaders ++ List("Metadata" -> """{"metadata":{"foo":"bar","humpty":"dumpty"}}"""), path)
    }

    "send the metadata header with properties" in new Setup {
      val additionalHeaders          = List("x-request-id" -> "abcdefgh1234567890", "any-old-header-name" -> "any-old-header-value")
      val expectedMetadataProperties = Map(
        "prop1" -> "value1",
        "prop2" -> "value2"
      )
      val sdesRequest                =
        SdesRequest(headers = additionalHeaders, metadata = Map("foo" -> "bar", "humpty" -> "dumpty"), body = base64EncodedString, metadataProperties = expectedMetadataProperties)
      val expectedStatus: Int        = OK
      primeStubForSuccess(responseBody, expectedStatus, path)

      val result: SendResult = await(underTest.postMessage(sdesRequest))

      result shouldBe SdesSuccess(responseBody)
      verifyRequestBody(simpleSdesRequest.body, path)

      verifyHeadersOnRequest(
        defaultHeaders ++ additionalHeaders ++ List(
          "Metadata" -> """{"metadata":{"foo":"bar","humpty":"dumpty","properties":[{"name":"prop1","value":"value1"},{"name":"prop2","value":"value2"}]}}"""
        ),
        path
      )
    }

    "return error statuses returned by SDES" in new Setup {
      val expectedStatus: Int = INTERNAL_SERVER_ERROR
      primeStubForSuccess(responseBody, expectedStatus, path)

      val result: SendResult = await(underTest.postMessage(simpleSdesRequest))

      result shouldBe SendFailExternal(expectedStatus)
      verifyHeadersOnRequest(defaultHeaders, path)
    }

    "return error status when soap fault is returned by the internal service" in new Setup {
      Seq(Fault.CONNECTION_RESET_BY_PEER, Fault.EMPTY_RESPONSE, Fault.MALFORMED_RESPONSE_CHUNK, Fault.RANDOM_DATA_THEN_CLOSE) foreach { fault =>
        primeStubForFault(responseBody, fault, path)

        val result: SendResult = await(underTest.postMessage(simpleSdesRequest))

        result shouldBe SendFailExternal(INTERNAL_SERVER_ERROR)
        verifyHeadersOnRequest(defaultHeaders, path)
      }
    }
  }

  private def verifyHeadersOnRequest(headers: Seq[(String, String)], path: String = "/") = {
    headers.foreach(headers => verifyHeader(headers._1, headers._2, path))
  }
}

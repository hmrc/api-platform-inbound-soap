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

package uk.gov.hmrc.apiplatforminboundsoap.services

import scala.concurrent.Future.successful
import scala.io.Source

import org.apache.pekko.stream.Materializer
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status.IM_A_TEAPOT
import play.api.mvc.Headers
import play.api.test.Helpers.{await, defaultAwaitTimeout}

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.connectors.InboundConnector
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendSuccess, SoapRequest}
import uk.gov.hmrc.apiplatforminboundsoap.xml.XmlHelper

class InboundMessageServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar with XmlHelper {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  def readFromFile(fileName: String) = {
    xml.XML.load(Source.fromResource(fileName).bufferedReader())
  }

  trait Setup {
    val inboundConnectorMock: InboundConnector = mock[InboundConnector]
    val httpStatus: Int                        = 200
    val appConfigMock: AppConfig               = mock[AppConfig]
    val xmlHelper: XmlHelper                   = mock[XmlHelper]

    val service: InboundMessageService =
      new InboundMessageService(appConfigMock, inboundConnectorMock)

    val forwardingUrl     = "some url"
    val testForwardingUrl = "some test url"
    when(appConfigMock.forwardMessageUrl).thenReturn(forwardingUrl)
    when(appConfigMock.testForwardMessageUrl).thenReturn(testForwardingUrl)

  }

  "processInboundMessage for production" should {
    val xmlBody = readFromFile("ie4n09-v2.xml")

    val forwardedHeaders = Headers(
      "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
      "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
      "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
      "x-files-included" -> isFileAttached(xmlBody).toString,
      "x-version-id"     -> getMessageVersion(xmlBody).displayName
    )

    "return success when connector returns success" in new Setup {
      val bodyCaptor   = ArgCaptor[SoapRequest]
      val headerCaptor = ArgCaptor[Headers]

      val inboundSoapMessage = SoapRequest(xmlBody.text, forwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)).thenReturn(successful(SendSuccess))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verify(appConfigMock, times(0)).testForwardMessageUrl
      verify(appConfigMock).forwardMessageUrl
      bodyCaptor hasCaptured inboundSoapMessage

    }

    "return failure when connector returns failure" in new Setup {
      val bodyCaptor         = ArgCaptor[SoapRequest]
      val headerCaptor       = ArgCaptor[Headers]
      val inboundSoapMessage = SoapRequest(xmlBody.text, forwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)).thenReturn(successful(SendFail(IM_A_TEAPOT)))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFail(IM_A_TEAPOT)
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verify(appConfigMock, times(0)).testForwardMessageUrl
      verify(appConfigMock).forwardMessageUrl
      bodyCaptor hasCaptured inboundSoapMessage
    }
  }

  "processInboundMessage for test" should {
    val xmlBody = readFromFile("ie4n09-v2.xml")

    val forwardedHeaders = Headers(
      "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
      "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
      "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
      "x-files-included" -> isFileAttached(xmlBody).toString,
      "x-version-id"     -> getMessageVersion(xmlBody).displayName
    )

    "return success when connector returns success" in new Setup {
      val bodyCaptor   = ArgCaptor[SoapRequest]
      val headerCaptor = ArgCaptor[Headers]

      val inboundSoapMessage = SoapRequest(xmlBody.text, testForwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)).thenReturn(successful(SendSuccess))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendSuccess
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verify(appConfigMock, times(0)).forwardMessageUrl
      verify(appConfigMock).testForwardMessageUrl
      bodyCaptor hasCaptured inboundSoapMessage

    }

    "return failure when connector returns failure" in new Setup {
      val bodyCaptor         = ArgCaptor[SoapRequest]
      val headerCaptor       = ArgCaptor[Headers]
      val inboundSoapMessage = SoapRequest(xmlBody.text, testForwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)).thenReturn(successful(SendFail(IM_A_TEAPOT)))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendFail(IM_A_TEAPOT)
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verify(appConfigMock, times(0)).forwardMessageUrl
      verify(appConfigMock).testForwardMessageUrl
      bodyCaptor hasCaptured inboundSoapMessage
    }
  }
}

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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.io.Source

import org.apache.pekko.stream.Materializer
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.http.Status.{IM_A_TEAPOT, SERVICE_UNAVAILABLE}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.connectors.InboundConnector
import uk.gov.hmrc.apiplatforminboundsoap.models.{SdesSendSuccess, SendFail, SendSuccess, SoapRequest}
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

class InboundMessageServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar with Ics2XmlHelper {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  def readFromFile(fileName: String) = {
    xml.XML.load(Source.fromResource(fileName).bufferedReader())
  }

  trait Setup {
    val inboundConnectorMock: InboundConnector = mock[InboundConnector]
    val ics2SdesService: Ics2SdesService       = mock[Ics2SdesService]
    val bodyCaptor                             = ArgCaptor[SoapRequest]
    val headerCaptor                           = ArgCaptor[Seq[(String, String)]]

    val httpStatus: Int          = Status.OK
    val appConfigMock: AppConfig = mock[AppConfig]
    val xmlHelper: Ics2XmlHelper = mock[Ics2XmlHelper]

    val service: InboundMessageService =
      new InboundMessageService(appConfigMock, inboundConnectorMock, ics2SdesService)

    val forwardingUrl     = "some url"
    val testForwardingUrl = "some test url"
    when(appConfigMock.forwardMessageUrl).thenReturn(forwardingUrl)
    when(appConfigMock.testForwardMessageUrl).thenReturn(testForwardingUrl)

  }

  "processInboundMessage for production" should {

    "return success when connector returns success" in new Setup {
      val xmlBody = readFromFile("ie4n09-v2.xml")

      val forwardedHeaders   = Seq[(String, String)](
        "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
        "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
        "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
        "x-files-included" -> isFileIncluded(xmlBody).toString,
        "x-version-id"     -> getMessageVersion(xmlBody).displayName
      )
      val inboundSoapMessage = SoapRequest(xmlBody.toString, forwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)(*)).thenReturn(successful(SendSuccess))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verify(appConfigMock, times(0)).testForwardMessageUrl
      verify(appConfigMock).forwardMessageUrl
      bodyCaptor hasCaptured inboundSoapMessage
    }

    "invoke SDESConnector when message contains embedded file attachment" in new Setup {
      val xmlBody = readFromFile("ie4s03-v2.xml")

      val forwardedHeaders   = Seq[(String, String)](
        "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
        "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
        "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
        "x-files-included" -> isFileIncluded(xmlBody).toString,
        "x-version-id"     -> getMessageVersion(xmlBody).displayName
      )
      val inboundSoapMessage = SoapRequest(xmlBody.toString(), forwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)(*)).thenReturn(successful(SendSuccess))
      when(ics2SdesService.processMessage(*)(*)).thenReturn(successful(List()))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess
      bodyCaptor hasCaptured inboundSoapMessage
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verify(ics2SdesService).processMessage(getBinaryElementsWithEmbeddedData(xmlBody))
    }

    "not invoke SDESConnector when message contains binary file with URI" in new Setup {
      val xmlBody = readFromFile("ie4r02-v2-binaryAttachment-with-uri.xml")

      val forwardedHeaders   = Seq[(String, String)](
        "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
        "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
        "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
        "x-files-included" -> isFileIncluded(xmlBody).toString,
        "x-version-id"     -> getMessageVersion(xmlBody).displayName
      )
      val inboundSoapMessage = SoapRequest(xmlBody.toString, forwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)(*)).thenReturn(successful(SendSuccess))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess
      bodyCaptor hasCaptured inboundSoapMessage
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verifyZeroInteractions(ics2SdesService)
    }

    "return fail status to caller and not forward message if any call to SDES fails when processing a message with 2 embedded files" in new Setup {
      val xmlBody = readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryAttachments-with-included-elements.xml")

      when(ics2SdesService.processMessage(refEq(getBinaryElementsWithEmbeddedData(xmlBody)))(*)).thenReturn(successful(List(
        SdesSendSuccess("sdes-uuid"),
        SendFail(SERVICE_UNAVAILABLE)
      )))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFail(SERVICE_UNAVAILABLE)
      verifyZeroInteractions(inboundConnectorMock)
    }

    "return failure when connector returns failure" in new Setup {
      val xmlBody = readFromFile("ie4n09-v2.xml")

      val forwardedHeaders   = Seq[(String, String)](
        "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
        "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
        "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
        "x-files-included" -> isFileIncluded(xmlBody).toString,
        "x-version-id"     -> getMessageVersion(xmlBody).displayName
      )
      val inboundSoapMessage = SoapRequest(xmlBody.toString, forwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)(*)).thenReturn(successful(SendFail(IM_A_TEAPOT)))

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

    val forwardedHeaders = Seq[(String, String)](
      "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
      "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
      "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
      "x-files-included" -> isFileIncluded(xmlBody).toString,
      "x-version-id"     -> getMessageVersion(xmlBody).displayName
    )

    "return success when connector returns success" in new Setup {
      val inboundSoapMessage = SoapRequest(xmlBody.toString, testForwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)(*)).thenReturn(successful(SendSuccess))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendSuccess
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verify(appConfigMock, times(0)).forwardMessageUrl
      verify(appConfigMock).testForwardMessageUrl
      bodyCaptor hasCaptured inboundSoapMessage

    }

    "return failure when connector returns failure" in new Setup {
      val inboundSoapMessage = SoapRequest(xmlBody.toString, testForwardingUrl)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)(*)).thenReturn(successful(SendFail(IM_A_TEAPOT)))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendFail(IM_A_TEAPOT)
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      verify(appConfigMock, times(0)).forwardMessageUrl
      verify(appConfigMock).testForwardMessageUrl
      bodyCaptor hasCaptured inboundSoapMessage
    }
  }
}

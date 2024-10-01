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
import scala.xml.NodeSeq

import org.apache.pekko.stream.Materializer
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.http.Status.IM_A_TEAPOT
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.ImportControlInboundSoapConnector
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.xml.XmlHelper

class InboundMessageServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar with XmlHelper {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  def readFromFile(fileName: String) = {
    xml.XML.load(Source.fromResource(fileName).bufferedReader())
  }

  trait Setup {
    val inboundConnectorMock: ImportControlInboundSoapConnector = mock[ImportControlInboundSoapConnector]
    val bodyCaptor                                              = ArgCaptor[NodeSeq]
    val headerCaptor                                            = ArgCaptor[Seq[(String, String)]]
    val isTestCaptor                                            = ArgCaptor[Boolean]

    val httpStatus: Int      = Status.OK
    val xmlHelper: XmlHelper = mock[XmlHelper]

    val service: InboundMessageService =
      new InboundMessageService(inboundConnectorMock)

  }

  "processInboundMessage for production" should {
    val xmlBody = readFromFile("ie4n09-v2.xml")

    val forwardedHeaders = Seq[(String, String)](
      "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
      "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
      "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
      "x-files-included" -> isFileAttached(xmlBody).toString,
      "x-version-id"     -> getMessageVersion(xmlBody).displayName
    )

    "return success when connector returns success" in new Setup {
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendSuccess
      verify(inboundConnectorMock).postMessage(xmlBody, forwardedHeaders, true)
      bodyCaptor hasCaptured xmlBody

    }

    "return failure when connector returns failure" in new Setup {
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendFail(IM_A_TEAPOT)))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendFail(IM_A_TEAPOT)
      verify(inboundConnectorMock).postMessage(xmlBody, forwardedHeaders, true)
      bodyCaptor hasCaptured xmlBody
    }
  }

  "processInboundMessage for test" should {
    val xmlBody = readFromFile("ie4n09-v2.xml")

    val forwardedHeaders = Seq[(String, String)](
      "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
      "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
      "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
      "x-files-included" -> isFileAttached(xmlBody).toString,
      "x-version-id"     -> getMessageVersion(xmlBody).displayName
    )

    "return success when connector returns success" in new Setup {
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendSuccess
      verify(inboundConnectorMock).postMessage(xmlBody, forwardedHeaders, true)
      bodyCaptor hasCaptured xmlBody

    }

    "return failure when connector returns failure" in new Setup {
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendFail(IM_A_TEAPOT)))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendFail(IM_A_TEAPOT)
      verify(inboundConnectorMock).postMessage(xmlBody, forwardedHeaders, true)
      bodyCaptor hasCaptured xmlBody
    }
  }
}

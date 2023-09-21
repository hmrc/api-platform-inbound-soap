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


import akka.stream.Materializer
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
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendSuccess, SoapRequest, Version1}
import uk.gov.hmrc.apiplatforminboundsoap.xml.XmlHelper
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future.successful

class InboundMessageServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {
    val inboundConnectorMock: InboundConnector = mock[InboundConnector]
    val httpStatus: Int = 200
    val appConfigMock: AppConfig = mock[AppConfig]
    val xmlHelper: XmlHelper = mock[XmlHelper]

    val service: InboundMessageService =
      new InboundMessageService(appConfigMock, xmlHelper, inboundConnectorMock)
    when(xmlHelper.isFileAttached(*)).thenReturn(true)
    when(xmlHelper.getMessageId(*)).thenReturn("427b9e3c-d708-4893-b5fa-21b5641231e5")
    when(xmlHelper.isFileAttached(*)).thenReturn(true)
    when(xmlHelper.getMessageVersion(*)).thenReturn(Version1)
    when(xmlHelper.getSoapAction(*)).thenReturn("CCN2.Service.Customs.EU.ICS.NESReferralBAS/IE4R02provideAdditionalInformation")
  }

  "processInboundMessage" should {
    val xmlBody = xml.XML.loadString("<xml>blah</xml>")
    val soapAction = "CCN2.Service.Customs.EU.ICS.NESReferralBAS/IE4R02provideAdditionalInformation"
    val messageId = "427b9e3c-d708-4893-b5fa-21b5641231e5"
    val messageVersion = "V1"
    val filesIncluded = "true"

    val receivedRequestHeaders = Headers(
      "server" -> "anyvalue",
      "x-envoy-upstream-service-time" -> "anyothervalue"
    )
    val forwardedHeaders = Headers(
      "x-soap-action" -> soapAction,
      "x-correlation-id" -> messageId,
      "x-message-id" -> messageId,
      "x-files-included" -> filesIncluded,
      "x-version-id" -> messageVersion)
    val forwardingUrl = "some url"
    val inboundSoapMessage = SoapRequest(xmlBody.text, forwardingUrl)

    "return success when connector returns success" in new Setup {
      val bodyCaptor = ArgCaptor[SoapRequest]
      val headerCaptor = ArgCaptor[Headers]
      val hcCaptor = ArgCaptor[HeaderCarrier]
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)).thenReturn(successful(SendSuccess))
      when(appConfigMock.forwardMessageUrl).thenReturn(forwardingUrl)

      val result = await(service.processInboundMessage(xmlBody, receivedRequestHeaders))

      result shouldBe SendSuccess
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      bodyCaptor hasCaptured inboundSoapMessage
//      headerCaptor hasCaptured forwardedHeaders

    }

    "return failure when connector returns failure" in new Setup {
      val bodyCaptor = ArgCaptor[SoapRequest]
      val headerCaptor = ArgCaptor[Headers]
      val hcCaptor = ArgCaptor[HeaderCarrier]
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor)).thenReturn(successful(SendFail(IM_A_TEAPOT)))
      when(appConfigMock.forwardMessageUrl).thenReturn(forwardingUrl)

      val result = await(service.processInboundMessage(xmlBody, receivedRequestHeaders))

      result shouldBe SendFail(IM_A_TEAPOT)
      verify(inboundConnectorMock).postMessage(inboundSoapMessage, forwardedHeaders)
      bodyCaptor hasCaptured inboundSoapMessage
//      headerCaptor hasCaptured forwardedHeaders
    }
  }
}

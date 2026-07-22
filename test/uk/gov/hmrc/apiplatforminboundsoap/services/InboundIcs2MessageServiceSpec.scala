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
import scala.io.Source
import scala.xml.{Elem, NodeSeq}

import org.apache.pekko.stream.Materializer
import org.mockito.Mockito.*
import org.mockito.captor.ArgCaptor
import org.mockito.quality.Strictness
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.http.Status.{ACCEPTED, IM_A_TEAPOT, OK, SERVICE_UNAVAILABLE}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.*
import uk.gov.hmrc.apiplatforminboundsoap.mocks.connectors.ImportControlInboundSoapMockModule
import uk.gov.hmrc.apiplatforminboundsoap.mocks.services.Ics2SdesServiceMockModule
import uk.gov.hmrc.apiplatforminboundsoap.models.*
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

class InboundIcs2MessageServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with IdiomaticMockito with Ics2XmlHelper {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  def readFromFile(fileName: String) = {
    xml.XML.load(Source.fromResource(fileName).bufferedReader())
  }

  trait Setup extends ImportControlInboundSoapMockModule with Ics2SdesServiceMockModule {
    val sdesConnectorConfig: SdesConnector.Config = mock[SdesConnector.Config](withSettings.strictness(Strictness.LENIENT))
    val bodyCaptor                                = ArgCaptor[NodeSeq]
    val wholeMessageCaptor                        = ArgCaptor[NodeSeq]
    val binaryElementsCaptor                      = ArgCaptor[NodeSeq]
    val headerCaptor                              = ArgCaptor[Seq[(String, String)]]
    val sdesRequestHeaderCaptor                   = ArgCaptor[Seq[(String, String)]]
    val isTestCaptor                              = ArgCaptor[Boolean]

    val httpStatus: Int          = Status.OK
    val xmlHelper: Ics2XmlHelper = mock[Ics2XmlHelper]

    val service: InboundIcs2MessageService =
      new InboundIcs2MessageService(ImportControlInboundSoapMock.theMock, Ics2SdesServiceMock.theMock, sdesConnectorConfig)
    when(sdesConnectorConfig.ics2).thenReturn(Ics2(srn = "srn", informationType = "infoType"))
  }

  "processInboundMessage for production" should {
    val xmlBody = readFromFile("ie4n09-v2.xml")
    "return success when connector returns success" in new Setup {
      ImportControlInboundSoapMock.PostMessage.succeedsWithCaptors(bodyCaptor, headerCaptor, isTestCaptor, OK, "some body")

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendSuccess(OK, "some body")
      ImportControlInboundSoapMock.PostMessage.verifyCalled()
      bodyCaptor.hasCaptured(xmlBody)
    }

    "invoke SDESConnector when message contains embedded file attachment" in new Setup {
      val xmlBody          = readFromFile("ie4s03-v2.xml")
      val forwardedXmlBody = readFromFile("post-sdes-processing/ie4s03-v2.xml")

      val forwardedHeaders: Seq[(String, String)] = getForwardedHeaders(xmlBody)

      ImportControlInboundSoapMock.PostMessage.succeedsWithCaptors(bodyCaptor, headerCaptor, isTestCaptor, OK, "some body")
      Ics2SdesServiceMock.ProcessMessage.succeeds(xmlBody, "test-filename.txt" -> "some-uuid-like-string")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess(OK, "some body")

      Ics2SdesServiceMock.ProcessMessage.verifyCalledWithBody(xmlBody)
      bodyCaptor.hasCaptured(forwardedXmlBody)
      headerCaptor.hasCaptured(forwardedHeaders)
      ImportControlInboundSoapMock.PostMessage.verifyCalled()
    }

    "ensure SDES UUID is encoded when config demands it" in new Setup {
      val xmlBody          = readFromFile("ie4r02-v2-one-binary-attachment.xml")
      val forwardedXmlBody = readFromFile("post-sdes-processing/ie4r02-v2-one-binary-attachment-base64-encode.xml")

      val forwardedHeaders: Seq[(String, String)] = getForwardedHeaders(xmlBody)

      when(sdesConnectorConfig.ics2).thenReturn(Ics2(srn = "srn", informationType = "infoType", encodeSdesReference = true))
      ImportControlInboundSoapMock.PostMessage.succeedsWithCaptors(bodyCaptor, headerCaptor, isTestCaptor, OK, "some body")
      Ics2SdesServiceMock.ProcessMessage.succeeds(xmlBody, "test-filename.txt" -> "some-uuid-like-string")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess(OK, "some body")
      bodyCaptor.hasCaptured(forwardedXmlBody)
      headerCaptor.hasCaptured(forwardedHeaders)
      Ics2SdesServiceMock.ProcessMessage.verifyCalledWithBody(xmlBody)
      ImportControlInboundSoapMock.PostMessage.verifyCalledWithBody(forwardedXmlBody)
    }

    "not forward message when embedded file attachment wasn't replaced" in new Setup {
      val xmlBody = readFromFile("ie4s03-v2.xml")

      Ics2SdesServiceMock.ProcessMessage.succeeds(xmlBody, "filename-not-in-xml.txt" -> "anything")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFailExternal("Failed to replace all embedded attachments for files Set(filename-not-in-xml.txt)", Status.UNPROCESSABLE_ENTITY)
      ImportControlInboundSoapMock.PostMessage.verifyNotCalled()
      Ics2SdesServiceMock.ProcessMessage.verifyCalledWithBody(xmlBody)
    }

    "not invoke SDESConnector when embedded file attachment filename is missing" in new Setup {
      val xmlBody = readFromFile("ie4s03-v2.xml")

      Ics2SdesServiceMock.ProcessMessage.succeeds(xmlBody, "" -> "anything")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFailExternal("Failed to replace all embedded attachments for files Set()", Status.UNPROCESSABLE_ENTITY)
      ImportControlInboundSoapMock.PostMessage.verifyNotCalled()
      Ics2SdesServiceMock.ProcessMessage.verifyCalledWithBody(xmlBody)
    }

    "not invoke SDESConnector when message contains binary file with URI" in new Setup {
      val xmlBody = readFromFile("ie4r02-v2-binaryAttachment-with-uri.xml")

      val forwardedHeaders: Seq[(String, String)] = getForwardedHeaders(xmlBody)
      ImportControlInboundSoapMock.PostMessage.succeedsWithCaptors(bodyCaptor, headerCaptor, isTestCaptor, OK, "some body")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess(OK, "some body")
      bodyCaptor.hasCaptured(xmlBody)
      headerCaptor.hasCaptured(forwardedHeaders)
      ImportControlInboundSoapMock.PostMessage.verifyCalledWithBodyAndHeaders(xmlBody, forwardedHeaders)
      Ics2SdesServiceMock.ProcessMessage.verifyNotCalled()
    }

    "not invoke SDESConnector when message contains binary file with missing filename attribute" in new Setup {
      val xmlBody = readFromFile("filename/ie4r02-v2-missing-filename-element.xml")

      Ics2SdesServiceMock.ProcessMessage.abortsBeforeSending("validation")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendNotAttempted("validation")
      ImportControlInboundSoapMock.PostMessage.verifyNotCalled()
    }

    "not invoke SDESConnector when message contains binary file with zero-length filename attribute" in new Setup {
      val xmlBody = readFromFile("filename/ie4r02-v2-blank-filename-element.xml")

      Ics2SdesServiceMock.ProcessMessage.abortsBeforeSending("validation")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendNotAttempted("validation")
      ImportControlInboundSoapMock.PostMessage.verifyNotCalled()
      Ics2SdesServiceMock.ProcessMessage.verifyCalledWithBody(xmlBody)
    }

    "invoke SDESConnector only once when two binary elements are included but one has only a URI" in new Setup {
      val xmlBody          = readFromFile("uriAndBinaryObject/ie4r02-v2-both-binaryFile-with-uri-and-binaryAttachment-with-included-elements.xml")
      val forwardedXmlBody = readFromFile("post-sdes-processing/ie4r02-v2-both-binaryFile-with-uri-and-binaryAttachment-with-included-elements.xml")

      val forwardedHeaders = getForwardedHeaders(xmlBody)

      Ics2SdesServiceMock.ProcessMessage.succeeds(xmlBody, "filename1.pdf" -> "some-uuid-like-string")
      ImportControlInboundSoapMock.PostMessage.succeeds(forwardedXmlBody, forwardedHeaders, OK, "some body")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess(OK, "some body")
      Ics2SdesServiceMock.ProcessMessage.verifyCalledWithBody(xmlBody)
      Ics2SdesServiceMock.ProcessMessage.verifyNotCalledAgain()
      ImportControlInboundSoapMock.PostMessage.verifyCalledWithBodyAndHeaders(forwardedXmlBody, forwardedHeaders)
    }

    "return fail status to caller and not forward message if any call to SDES fails when processing a message with 2 embedded files" in new Setup {
      val xmlBody = readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryAttachments-with-included-elements.xml")

      Ics2SdesServiceMock.ProcessMessage.notAllRequestsSucceed("some error", SERVICE_UNAVAILABLE)

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFailExternal("some error", SERVICE_UNAVAILABLE)
      ImportControlInboundSoapMock.PostMessage.verifyNotCalled()
    }

    "return failure when connector returns failure" in new Setup {
      val forwardedHeaders = getForwardedHeaders(xmlBody)
      ImportControlInboundSoapMock.PostMessage.failsInSending(xmlBody, forwardedHeaders, IM_A_TEAPOT, "some error")

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFailExternal("some error", IM_A_TEAPOT)
      ImportControlInboundSoapMock.PostMessage.verifyCalledWithBodyAndHeaders(xmlBody, forwardedHeaders)
    }
  }

  "processInboundMessage for test" should {
    val xmlBody = readFromFile("ie4n09-v2.xml")
    "return success when connector returns success" in new Setup {
      ImportControlInboundSoapMock.PostMessage.succeedsWithCaptors(bodyCaptor, headerCaptor, isTestCaptor, ACCEPTED, "some body")

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendSuccess(ACCEPTED, "some body")

      ImportControlInboundSoapMock.PostMessage.verifyCalledWithBody(xmlBody)
      bodyCaptor.hasCaptured(xmlBody)
      isTestCaptor.hasCaptured(true)
    }

    "return failure when connector returns failure" in new Setup {
      val forwardedHeaders = getForwardedHeaders(xmlBody)
      ImportControlInboundSoapMock.PostMessage.failsInSending(xmlBody, forwardedHeaders, IM_A_TEAPOT, "some error", true)

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendFailExternal("some error", IM_A_TEAPOT)
      ImportControlInboundSoapMock.PostMessage.verifyCalledWithBody(xmlBody)
    }

  }

  private def getForwardedHeaders(xmlBody: Elem) = {
    Seq[(String, String)](
      "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
      "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
      "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
      "x-files-included" -> isFileIncluded(xmlBody).toString,
      "x-version-id"     -> "V2"
    )
  }
}

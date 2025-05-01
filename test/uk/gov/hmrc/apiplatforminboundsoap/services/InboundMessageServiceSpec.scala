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
import scala.xml.NodeSeq

import org.apache.pekko.stream.Materializer
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.http.Status.{ACCEPTED, IM_A_TEAPOT, OK, SERVICE_UNAVAILABLE}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.Ics2
import uk.gov.hmrc.apiplatforminboundsoap.connectors.{ImportControlInboundSoapConnector, SdesConnector}
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

class InboundMessageServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar with Ics2XmlHelper {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  def readFromFile(fileName: String) = {
    xml.XML.load(Source.fromResource(fileName).bufferedReader())
  }

  trait Setup {
    val ics2SdesServiceMock: Ics2SdesService                    = mock[Ics2SdesService]
    val inboundConnectorMock: ImportControlInboundSoapConnector = mock[ImportControlInboundSoapConnector]
    val sdesConnectorConfig: SdesConnector.Config               = mock[SdesConnector.Config]
    val bodyCaptor                                              = ArgCaptor[NodeSeq]
    val wholeMessageCaptor                                      = ArgCaptor[NodeSeq]
    val binaryElementsCaptor                                    = ArgCaptor[NodeSeq]
    val headerCaptor                                            = ArgCaptor[Seq[(String, String)]]
    val sdesRequestHeaderCaptor                                 = ArgCaptor[Seq[(String, String)]]
    val isTestCaptor                                            = ArgCaptor[Boolean]

    val httpStatus: Int          = Status.OK
    val xmlHelper: Ics2XmlHelper = mock[Ics2XmlHelper]

    val service: InboundMessageService =
      new InboundMessageService(inboundConnectorMock, ics2SdesServiceMock, sdesConnectorConfig)
    when(sdesConnectorConfig.ics2) thenReturn Ics2(srn = "srn", informationType = "infoType", uploadPath = "some/path")
  }

  "processInboundMessage for production" should {
    val xmlBody = readFromFile("ie4n09-v2.xml")

    val forwardedHeaders = Seq[(String, String)](
      "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
      "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
      "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
      "x-files-included" -> isFileIncluded(xmlBody).toString,
      "x-version-id"     -> "V2"
    )

    "return success when connector returns success" in new Setup {
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendSuccess(OK)
      verify(inboundConnectorMock).postMessage(xmlBody, forwardedHeaders, true)
      bodyCaptor hasCaptured xmlBody
    }

    "invoke SDESConnector when message contains embedded file attachment" in new Setup {
      val xmlBody          = readFromFile("ie4s03-v2.xml")
      val binaryElement    = getBinaryElementsWithEmbeddedData(xmlBody)
      val forwardedXmlBody = readFromFile("post-sdes-processing/ie4s03-v2.xml")

      val forwardedHeaders = Seq[(String, String)](
        "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
        "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
        "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
        "x-files-included" -> isFileIncluded(xmlBody).toString,
        "x-version-id"     -> "V2"
      )

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(ics2SdesServiceMock.processMessage(wholeMessageCaptor, binaryElementsCaptor)(*)).thenReturn(successful(List(SdesSuccessResult(SdesReference(
        "test-filename.txt",
        "some-uuid-like-string"
      )))))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess(OK)
      bodyCaptor hasCaptured forwardedXmlBody
      wholeMessageCaptor hasCaptured xmlBody
      binaryElementsCaptor hasCaptured binaryElement
      headerCaptor hasCaptured forwardedHeaders
      verify(inboundConnectorMock).postMessage(forwardedXmlBody, forwardedHeaders, false)
      verify(ics2SdesServiceMock).processMessage(xmlBody, binaryElement)
    }

    "ensure SDES UUID is encoded when config demands it" in new Setup {
      val xmlBody          = readFromFile("ie4r02-v2-one-binary-attachment.xml")
      val binaryElement    = getBinaryElementsWithEmbeddedData(xmlBody)
      val forwardedXmlBody = readFromFile("post-sdes-processing/ie4r02-v2-one-binary-attachment-base64-encode.xml")

      val forwardedHeaders = Seq[(String, String)](
        "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
        "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
        "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
        "x-files-included" -> isFileIncluded(xmlBody).toString,
        "x-version-id"     -> "V2"
      )

      when(sdesConnectorConfig.ics2) thenReturn Ics2(srn = "srn", informationType = "infoType", uploadPath = "some/path", encodeSdesReference = true)
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(ics2SdesServiceMock.processMessage(wholeMessageCaptor, binaryElementsCaptor)(*)).thenReturn(successful(List(SdesSuccessResult(SdesReference(
        "test-filename.txt",
        "some-uuid-like-string"
      )))))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess(OK)
      bodyCaptor hasCaptured forwardedXmlBody
      wholeMessageCaptor hasCaptured xmlBody
      binaryElementsCaptor hasCaptured binaryElement
      headerCaptor hasCaptured forwardedHeaders
      verify(inboundConnectorMock).postMessage(forwardedXmlBody, forwardedHeaders, false)
      verify(ics2SdesServiceMock).processMessage(xmlBody, binaryElement)
    }

    "not invoke SDESConnector when embedded file attachment wasn't replaced" in new Setup {
      val xmlBody       = readFromFile("ie4s03-v2.xml")
      val binaryElement = getBinaryElementsWithEmbeddedData(xmlBody)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(ics2SdesServiceMock.processMessage(*, *)(*)).thenReturn(successful(List(SdesSuccessResult(SdesReference("filename-not-in-xml.txt", "anything")))))
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(ics2SdesServiceMock.processMessage(*, *)(*)).thenReturn(successful(List(SdesSuccessResult(SdesReference("filename-not-in-xml.txt", "anything")))))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFailExternal(Status.UNPROCESSABLE_ENTITY)
      verifyZeroInteractions(inboundConnectorMock)
      verify(ics2SdesServiceMock).processMessage(xmlBody, binaryElement)
    }

    "not invoke SDESConnector when embedded file attachment filename is missing" in new Setup {
      val xmlBody       = readFromFile("ie4s03-v2.xml")
      val binaryElement = getBinaryElementsWithEmbeddedData(xmlBody)

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(ics2SdesServiceMock.processMessage(*, *)(*)).thenReturn(successful(List(SdesSuccessResult(SdesReference("", "anything")))))
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(ics2SdesServiceMock.processMessage(*, *)(*)).thenReturn(successful(List(SdesSuccessResult(SdesReference("", "anything")))))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFailExternal(Status.UNPROCESSABLE_ENTITY)
      verifyZeroInteractions(inboundConnectorMock)
      verify(ics2SdesServiceMock).processMessage(xmlBody, binaryElement)
    }

    "not invoke SDESConnector when message contains binary file with URI" in new Setup {
      val xmlBody = readFromFile("ie4r02-v2-binaryAttachment-with-uri.xml")

      val forwardedHeaders = Seq[(String, String)](
        "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
        "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
        "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
        "x-files-included" -> isFileIncluded(xmlBody).toString,
        "x-version-id"     -> "V2"
      )
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess(OK)
      bodyCaptor hasCaptured xmlBody
      headerCaptor hasCaptured forwardedHeaders
      verify(inboundConnectorMock).postMessage(xmlBody, forwardedHeaders, false)
      verifyZeroInteractions(ics2SdesServiceMock)
    }

    "not invoke SDESConnector when message contains binary file with missing filename attribute" in new Setup {
      val xmlBody = readFromFile("filename/ie4r02-v2-missing-filename-element.xml")

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(ics2SdesServiceMock.processMessage(*, *)(*)).thenReturn(successful(List(SendNotAttempted("validation"))))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendNotAttempted("validation")
      verifyZeroInteractions(inboundConnectorMock)
    }

    "not invoke SDESConnector when message contains binary file with zero-length filename attribute" in new Setup {
      val xmlBody = readFromFile("filename/ie4r02-v2-blank-filename-element.xml")

      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(ics2SdesServiceMock.processMessage(*, *)(*)).thenReturn(successful(List(SendNotAttempted("validation"))))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendNotAttempted("validation")
      verifyZeroInteractions(inboundConnectorMock)
    }

    "invoke SDESConnector only once when two binary elements are included but one has only a URI" in new Setup {
      val xmlBody          = readFromFile("uriAndBinaryObject/ie4r02-v2-both-binaryFile-with-uri-and-binaryAttachment-with-included-elements.xml")
      val binaryElement    = getBinaryElementsWithEmbeddedData(xmlBody)
      val forwardedXmlBody = readFromFile("post-sdes-processing/ie4r02-v2-both-binaryFile-with-uri-and-binaryAttachment-with-included-elements.xml")

      val forwardedHeaders = Seq[(String, String)](
        "x-soap-action"    -> getSoapAction(xmlBody).getOrElse(""),
        "x-correlation-id" -> getMessageId(xmlBody).getOrElse(""),
        "x-message-id"     -> getMessageId(xmlBody).getOrElse(""),
        "x-files-included" -> isFileIncluded(xmlBody).toString,
        "x-version-id"     -> "V2"
      )
      when(ics2SdesServiceMock.processMessage(*, *)(*)).thenReturn(successful(List(SdesSuccessResult(SdesReference("filename1.pdf", "some-uuid-like-string")))))
      when(inboundConnectorMock.postMessage(*, *, *)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendSuccess(OK)
      verify(ics2SdesServiceMock).processMessage(xmlBody, binaryElement)
      verifyNoMoreInteractions(ics2SdesServiceMock)
      verify(inboundConnectorMock).postMessage(forwardedXmlBody, forwardedHeaders, false)
    }

    "return fail status to caller and not forward message if any call to SDES fails when processing a message with 2 embedded files" in new Setup {
      val xmlBody        = readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryAttachments-with-included-elements.xml")
      val binaryElements = <urn:binaryAttachment>
  <urn:filename>filename1.pdf</urn:filename>
  <urn:MIME>application/pdf</urn:MIME>
  <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
  <urn:description>A PDFy sort of file</urn:description>
</urn:binaryAttachment>
  <urn:binaryAttachment>
    <urn:filename>filename2.txt</urn:filename>
    <urn:MIME>text/plain</urn:MIME>
    <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
    <urn:description>A texty sort of file</urn:description>
  </urn:binaryAttachment>
      when(ics2SdesServiceMock.processMessage(refEq(xmlBody), refEq(binaryElements))(*)).thenReturn(successful(List(
        SdesSuccess("sdes-uuid"),
        SendFailExternal(SERVICE_UNAVAILABLE)
      )))

      val result = await(service.processInboundMessage(xmlBody))

      result shouldBe SendFailExternal(SERVICE_UNAVAILABLE)
      verifyZeroInteractions(inboundConnectorMock)
    }

    "return failure when connector returns failure" in new Setup {
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendFailExternal(IM_A_TEAPOT)))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendFailExternal(IM_A_TEAPOT)
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
      "x-files-included" -> isFileIncluded(xmlBody).toString,
      "x-version-id"     -> "V2"
    )

    "return success when connector returns success" in new Setup {
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendSuccess(ACCEPTED)))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendSuccess(ACCEPTED)
      verify(inboundConnectorMock).postMessage(xmlBody, forwardedHeaders, true)
      bodyCaptor hasCaptured xmlBody

    }

    "return failure when connector returns failure" in new Setup {
      when(inboundConnectorMock.postMessage(bodyCaptor, headerCaptor, isTestCaptor)(*)).thenReturn(successful(SendFailExternal(IM_A_TEAPOT)))

      val result = await(service.processInboundMessage(xmlBody, isTest = true))

      result shouldBe SendFailExternal(IM_A_TEAPOT)
      verify(inboundConnectorMock).postMessage(xmlBody, forwardedHeaders, true)
      bodyCaptor hasCaptured xmlBody
    }
  }
}

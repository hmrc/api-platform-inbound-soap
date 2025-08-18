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

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.io.Source
import scala.xml.Elem

import org.apache.pekko.stream.Materializer
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.Ics2
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

class Ics2SdesServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar with Ics2XmlHelper {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  def readFromFile(fileName: String) = {
    xml.XML.load(Source.fromResource(fileName).bufferedReader())
  }

  trait Setup {
    val sdesConnectorMock: SdesConnector = mock[SdesConnector]
    val bodyCaptor                       = ArgCaptor[SdesRequest]
    val headerCaptor                     = ArgCaptor[Seq[(String, String)]]

    val httpStatus: Int                     = Status.OK
    val appConfigMock: SdesConnector.Config = mock[SdesConnector.Config]
    val xmlHelper: Ics2XmlHelper            = mock[Ics2XmlHelper]

    val service: Ics2SdesService =
      new Ics2SdesService(appConfigMock, sdesConnectorMock)

    val sdesUrl = "SDES url"
    val ics2    = Ics2(srn = "ICS2 SRN", informationType = "ICS2 info type")
    when(appConfigMock.baseUrl).thenReturn(sdesUrl)
    when(appConfigMock.ics2).thenReturn(ics2)
  }

  "processMessage" should {
    "return success when connector returns success" in new Setup {
      val expectedSdesUuid           = UUID.randomUUID().toString
      val xmlBody: Elem              = readFromFile("ie4s03-v2.xml")
      val binaryElement: Elem        = <urn:binaryFile>
        <urn:filename>test-filename.txt</urn:filename>
        <urn:MIME>application/pdf</urn:MIME>
        <urn:includedBinaryObject>cid:1177341525550</urn:includedBinaryObject>
        <urn:description>a file made up for unit testing</urn:description>
      </urn:binaryFile>
      val expectedMetadata           = Map(
        "srn"             -> ics2.srn,
        "informationType" -> ics2.informationType,
        "filename"        -> "test-filename.txt"
      )
      val expectedMetadataProperties = Map(
        "messageId"   -> "ad7f2ad2d4f5-4606-99a0-0dd4e52be116",
        "fileMIME"    -> "application/pdf",
        "description" -> "a file made up for unit testing",
        "MRN"         -> "7c1aa850-9760-42ab-bebe-709e3a4a888f"
      )
      val expectedBody               = "cid:1177341525550"
      val expectedSdesRequest        = SdesRequest(Seq.empty, expectedMetadata, expectedMetadataProperties, expectedBody)
      val expectedServiceResult      = SdesSuccessResult(SdesReference(uuid = expectedSdesUuid, forFilename = "test-filename.txt"))

      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SdesSuccess(expectedSdesUuid)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(expectedServiceResult)
      verify(sdesConnectorMock).postMessage(expectedSdesRequest)
      bodyCaptor hasCaptured expectedSdesRequest
    }

    "make two requests to SDES when XML message contains two binaryAttachment elements" in new Setup {
      val expectedSdesUuidForFirstCall            = UUID.randomUUID().toString
      val expectedFilenameForFirstCall            = "filename1.pdf"
      val expectedSdesUuidForSecondCall           = UUID.randomUUID().toString
      val expectedFilenameForSecondCall           = "filename2.txt"
      val xmlBody: Elem                           = readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryAttachments-with-included-elements.xml")
      val binaryElements                          = <urn:binaryAttachment>
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
      val expectedFirstRequestMetadata            = Map(
        "informationType" -> ics2.informationType,
        "filename"        -> "filename1.pdf",
        "srn"             -> ics2.srn
      )
      val expectedSecondRequestMetadata           = Map(
        "srn"             -> ics2.srn,
        "informationType" -> ics2.informationType,
        "filename"        -> "filename2.txt"
      )
      val expectedFirstRequestMetadataProperties  = Map(
        "messageId"                -> "ad7f2ad2d4f5-4606-99a0-0dd4e52be116",
        "description"              -> "A PDFy sort of file",
        "fileMIME"                 -> "application/pdf",
        "MRN"                      -> "7c1aa850-9760-42ab",
        "referralRequestReference" -> "d4af29b4-d1d7-4f42-a186-ca5a71fabeb"
      )
      val expectedSecondRequestMetadataProperties = Map(
        "messageId"                -> "ad7f2ad2d4f5-4606-99a0-0dd4e52be116",
        "description"              -> "A texty sort of file",
        "fileMIME"                 -> "text/plain",
        "MRN"                      -> "7c1aa850-9760-42ab",
        "referralRequestReference" -> "d4af29b4-d1d7-4f42-a186-ca5a71fabeb"
      )
      val expectedBody                            = "dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo="
      val expectedFirstSdesRequest                = SdesRequest(Seq.empty, expectedFirstRequestMetadata, expectedFirstRequestMetadataProperties, expectedBody)
      val expectedSecondSdesRequest               = SdesRequest(Seq.empty, expectedSecondRequestMetadata, expectedSecondRequestMetadataProperties, expectedBody)

      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SdesSuccess(expectedSdesUuidForFirstCall)))
        .andThen(successful(SdesSuccess(expectedSdesUuidForSecondCall)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(
        SdesSuccessResult(SdesReference(expectedFilenameForFirstCall, expectedSdesUuidForFirstCall)),
        SdesSuccessResult(SdesReference(expectedFilenameForSecondCall, expectedSdesUuidForSecondCall))
      )
      verify(sdesConnectorMock, times(2)).postMessage(*)(*)

      bodyCaptor.hasCaptured(expectedFirstSdesRequest, expectedSecondSdesRequest)
    }

    "return invalid response when message does not contain includedBinaryObject" in new Setup {
      val xmlBody: Elem  = readFromFile("uriAndBinaryObject/ie4r02-v2-missing-uri-and-includedBinaryObject-element.xml")
      val binaryElements = <urn:binaryAttachment>
  <urn:filename>?</urn:filename>
  <urn:MIME>?</urn:MIME>
  <urn:description>?</urn:description>
</urn:binaryAttachment>
      val result         = await(service.processMessage(xmlBody))

      result shouldBe List(SendNotAttempted("Argument includedBinaryObject was not found in XML"))
      verifyZeroInteractions(appConfigMock)
      verifyZeroInteractions(sdesConnectorMock)
    }

    "return invalid response when message's binaryFile block does not contain filename" in new Setup {
      val xmlBody: Elem = readFromFile("filename/ie4r02-v2-missing-filename-element.xml")
      val binaryElement = <urn:binaryAttachment>
        <!--Optional:-->
        <urn:MIME>?</urn:MIME>
        <!--Optional:-->
        <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
        <!--Optional:-->
        <urn:description>?</urn:description>
      </urn:binaryAttachment>
      val result        = await(service.processMessage(xmlBody))

      result shouldBe List(SendNotAttempted("Argument filename was not found in XML"))
      verifyZeroInteractions(appConfigMock)
      verifyZeroInteractions(sdesConnectorMock)
    }

    "return invalid response when message's binaryFile block contains empty filename" in new Setup {
      val xmlBody: Elem = readFromFile("filename/ie4r02-v2-blank-filename-element.xml")
      val binaryElement = <urn:binaryAttachment>
  <urn:filename></urn:filename>
  <urn:MIME>?</urn:MIME>
  <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
  <urn:description>?</urn:description>
</urn:binaryAttachment>
      val result        = await(service.processMessage(xmlBody))

      result shouldBe List(SendNotAttempted("Argument filename found in XML but is empty"))
      verifyZeroInteractions(appConfigMock)
      verifyZeroInteractions(sdesConnectorMock)
    }

    "return upstream response when message sending fails" in new Setup {
      val xmlBody: Elem = readFromFile("ie4s03-v2.xml")
      val binaryElement = <urn:binaryFile>
        <urn:filename>test-filename.txt</urn:filename>
        <urn:MIME>application/pdf</urn:MIME>
        <urn:includedBinaryObject>cid:1177341525550</urn:includedBinaryObject>
        <urn:description>a file made up for unit testing</urn:description>
      </urn:binaryFile>
      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SendFailExternal("some error", INTERNAL_SERVER_ERROR)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(SendFailExternal("500 returned from SDES call", INTERNAL_SERVER_ERROR))
    }
  }
}

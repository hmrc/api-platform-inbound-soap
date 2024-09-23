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

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector
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

    val httpStatus: Int          = Status.OK
    val appConfigMock: AppConfig = mock[AppConfig]
    val xmlHelper: Ics2XmlHelper = mock[Ics2XmlHelper]

    val service: Ics2SdesService =
      new Ics2SdesService(appConfigMock, sdesConnectorMock)

    val sdesUrl          = "SDES url"
    val sdesICS2SRN      = "ICS2 SRN"
    val sdesICS2InfoType = "ICS2 info type"
    when(appConfigMock.sdesUrl).thenReturn(sdesUrl)
    when(appConfigMock.ics2SdesSrn).thenReturn(sdesICS2SRN)
    when(appConfigMock.ics2SdesInfoType).thenReturn(sdesICS2InfoType)
  }

  "processMessage" should {

    "return success when connector returns success" in new Setup {
      val expectedSdesUuid      = UUID.randomUUID().toString
      val xmlBody: Elem         = readFromFile("ie4s03-v2.xml")
      val expectedMetadata      = Map(
        "srn"             -> sdesICS2SRN,
        "informationType" -> sdesICS2InfoType,
        "filename"        -> "test-filename.txt",
        "fileMIME"        -> "application/pdf",
        "description"     -> "a file made up for unit testing",
        "MRN"             -> "7c1aa850-9760-42ab-bebe-709e3a4a888f"
      )
      val expectedBody          = "cid:1177341525550"
      val expectedSdesRequest   = SdesRequest(Seq.empty, expectedMetadata, expectedBody, sdesUrl)
      val expectedServiceResult = SdesSendSuccessResult(SdesResult(uuid = expectedSdesUuid, forFilename = Some("test-filename.txt")))

      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SdesSendSuccess(expectedSdesUuid)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(expectedServiceResult)
      verify(sdesConnectorMock).postMessage(expectedSdesRequest)
      verify(appConfigMock).sdesUrl
      bodyCaptor hasCaptured expectedSdesRequest
    }

    "make two requests to SDES when XML message contains two binaryAttachment elements" in new Setup {
      val expectedSdesUuidForFirstCall  = UUID.randomUUID().toString
      val expectedFilenameForFirstCall  = Some("filename1.pdf")
      val expectedSdesUuidForSecondCall = UUID.randomUUID().toString
      val expectedFilenameForSecondCall = Some("filename2.txt")
      val xmlBody: Elem                 = readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryAttachments-with-included-elements.xml")
      val expectedFirstRequestMetadata  = Map(
        "description"              -> "A PDFy sort of file",
        "informationType"          -> sdesICS2InfoType,
        "filename"                 -> "filename1.pdf",
        "fileMIME"                 -> "application/pdf",
        "MRN"                      -> "7c1aa850-9760-42ab",
        "referralRequestReference" -> "d4af29b4-d1d7-4f42-a186-ca5a71fabeb",
        "srn"                      -> sdesICS2SRN
      )
      val expectedSecondRequestMetadata = Map(
        "srn"                      -> sdesICS2SRN,
        "informationType"          -> sdesICS2InfoType,
        "filename"                 -> "filename2.txt",
        "fileMIME"                 -> "text/plain",
        "description"              -> "A texty sort of file",
        "referralRequestReference" -> "d4af29b4-d1d7-4f42-a186-ca5a71fabeb",
        "MRN"                      -> "7c1aa850-9760-42ab"
      )
      val expectedBody                  = "dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo="
      val expectedFirstSdesRequest      = SdesRequest(Seq.empty, expectedFirstRequestMetadata, expectedBody, sdesUrl)
      val expectedSecondSdesRequest     = SdesRequest(Seq.empty, expectedSecondRequestMetadata, expectedBody, sdesUrl)

      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SdesSendSuccess(expectedSdesUuidForFirstCall)))
        .andThen(successful(SdesSendSuccess(expectedSdesUuidForSecondCall)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(
        SdesSendSuccessResult(SdesResult(expectedSdesUuidForFirstCall, expectedFilenameForFirstCall)),
        SdesSendSuccessResult(SdesResult(expectedSdesUuidForSecondCall, expectedFilenameForSecondCall))
      )
      verify(sdesConnectorMock, times(2)).postMessage(*)(*)
      verify(appConfigMock, times(2)).sdesUrl

      bodyCaptor.hasCaptured(expectedFirstSdesRequest, expectedSecondSdesRequest)
    }

    // this should never happen as validation in the ActionFilter associated with CCN2MessageController would block
    "return invalid response when message does not contain includedBinaryObject" in new Setup {
      val xmlBody: Elem = readFromFile("uriAndBinaryObject/ie4r02-v2-missing-uri-and-includedBinaryObject-element.xml")

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(SendNotAttempted("Argument includedBinaryObject is not valid base 64 data"))
      verifyZeroInteractions(appConfigMock)
      verifyZeroInteractions(sdesConnectorMock)
    }
    "return upstream response when message sending fails" in new Setup {
      val xmlBody: Elem = readFromFile("ie4s03-v2.xml")
      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SendFail(INTERNAL_SERVER_ERROR)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(SendFail(INTERNAL_SERVER_ERROR))
    }
  }
}
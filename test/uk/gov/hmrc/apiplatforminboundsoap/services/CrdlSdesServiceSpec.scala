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

import java.time.{Clock, Instant, ZoneId}
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
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.Crdl
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.xml.{Ics2XmlHelper, NoChangeTransformer}

class CrdlSdesServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  def readFromFile(fileName: String) = {
    xml.XML.load(Source.fromResource(fileName).bufferedReader())
  }

  trait Setup {
    val sdesConnectorMock: SdesConnector = mock[SdesConnector]
    val bodyCaptor                       = ArgCaptor[SdesRequest]
    val headerCaptor                     = ArgCaptor[Seq[(String, String)]]

    val sdesRequestTime: Instant            = Instant.parse("2020-01-02T03:04:05.006Z")
    val sdesRequestClock: Clock             = Clock.fixed(sdesRequestTime, ZoneId.of("UTC"))
    val httpStatus: Int                     = Status.OK
    val appConfigMock: SdesConnector.Config = mock[SdesConnector.Config]
    val xmlHelper: Ics2XmlHelper            = mock[Ics2XmlHelper]
    val xmlTransformer: NoChangeTransformer = new NoChangeTransformer()

    val service: CrdlSdesService =
      new CrdlSdesService(appConfigMock, sdesConnectorMock, sdesRequestClock, xmlTransformer)

    val sdesUrl                           = "SDES url"
    val crdlConfig                        = Crdl(srn = "CRDL SRN", informationType = "CRDL info type")

    val attachmentElementContents: String =
      "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPG5zMTpJbXBvcnRSZWZlcmVuY2VEYXRhRW50cnlSZXNwTXNnIHhzaTpzY2hlbWFMb2NhdGlvbj0iIiB4bWxucz0iaHR0cDovL3htbG5zLmVjLmV1L0J1c2luZXNzT2JqZWN0cy9DU1JEMi9SZWZlcmVuY2VEYXRhRW50cnlNYW5hZ2VtZW50QkFTU2VydmljZVR5cGUvVjQiIHhtbG5zOm5zMD0iaHR0cDovL3htbG5zLmVjLmV1L0J1c2luZXNzT2JqZWN0cy9DU1JEMi9Db21tb25TZXJ2aWNlVHlwZS9WMyIgeG1sbnM6bnMyPSJodHRwOi8veG1sbnMuZWMuZXUvQnVzaW5lc3NPYmplY3RzL0NTUkQyL01lc3NhZ2VIZWFkZXJUeXBlL1YyIiB4bWxuczpuczE9Imh0dHA6Ly94bWxucy5lYy5ldS9CdXNpbmVzc0FjdGl2aXR5U2VydmljZS9DU1JEMi9JUmVmZXJlbmNlRGF0YUVudHJ5TWFuYWdlbWVudEJBUy9WNCIgeG1sbnM6eHNpPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYS1pbnN0YW5jZSI+CiAgIDxuczA6TWVzc2FnZUhlYWRlci8+CiAgIDxuczA6YWNrbm93bGVkZ2VtZW50Pk9LPC9uczA6YWNrbm93bGVkZ2VtZW50Pgo8L25zMTpJbXBvcnRSZWZlcmVuY2VEYXRhRW50cnlSZXNwTXNnPgo="
    when(appConfigMock.baseUrl).thenReturn(sdesUrl)
    when(appConfigMock.crdl).thenReturn(crdlConfig)
  }

  "processMessage" should {
    "return success when connector returns success" in new Setup {
      val expectedSdesUuid           = UUID.randomUUID().toString
      val xmlBody: Elem              = readFromFile("crdl/crdl-request-well-formed.xml")
      val expectedMetadata           = Map(
        "srn"             -> crdlConfig.srn,
        "informationType" -> crdlConfig.informationType,
        "filename"        -> "referencedata_13933062_1577934245.txt.gz"
      )
      val expectedMetadataProperties = Map.empty[String, String]
      val expectedSdesRequest        = SdesRequest(Seq.empty, expectedMetadata, expectedMetadataProperties, attachmentElementContents)
      val expectedServiceResult      = SdesSuccess(uuid = expectedSdesUuid)

      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SdesSuccess(expectedSdesUuid)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(expectedServiceResult)
      verify(sdesConnectorMock).postMessage(expectedSdesRequest)
      bodyCaptor hasCaptured expectedSdesRequest
    }

    "process response when connector returns error" in new Setup {
      val xmlBody: Elem              = readFromFile("crdl/crdl-request-well-formed.xml")
      val expectedMetadata           = Map(
        "srn"             -> crdlConfig.srn,
        "informationType" -> crdlConfig.informationType,
        "filename"        -> "referencedata_13933062_1577934245.txt.gz"
      )
      val expectedMetadataProperties = Map.empty[String, String]
      val expectedSdesRequest        = SdesRequest(Seq.empty, expectedMetadata, expectedMetadataProperties, attachmentElementContents)
      val expectedServiceResult      = SendFailExternal("500 returned from SDES call due to some error", INTERNAL_SERVER_ERROR)

      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SendFailExternal("some error", INTERNAL_SERVER_ERROR)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(expectedServiceResult)
      verify(sdesConnectorMock).postMessage(expectedSdesRequest)
      bodyCaptor hasCaptured expectedSdesRequest
    }

    "not make a call to SDES when message contains empty attachment element" in new Setup {
      val xmlBody: Elem = readFromFile("crdl/crdl-request-empty-attachment-element.xml")

      val expectedServiceResult = SendNotAttempted("Embedded attachment element ReceiveReferenceDataRequestResult is empty")

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(expectedServiceResult)
      verifyZeroInteractions(sdesConnectorMock)
    }

    "not make a call to SDES when message has no attachment element" in new Setup {
      val xmlBody: Elem = readFromFile("crdl/crdl-request-missing-attachment-element.xml")

      val result = await(service.processMessage(xmlBody))

      result shouldBe List()
      verifyZeroInteractions(sdesConnectorMock)
    }

    "omit TaskIdentifier from SDES metadata header where not found in message" in new Setup {
      val xmlBody: Elem              = readFromFile("crdl/crdl-request-no-task-identifer.xml")
      val expectedSdesUuid           = UUID.randomUUID().toString
      val expectedMetadata           = Map(
        "srn"             -> crdlConfig.srn,
        "informationType" -> crdlConfig.informationType,
        "filename"        -> "referencedata__1577934245.txt.gz"
      )
      val expectedMetadataProperties = Map.empty[String, String]
      val expectedSdesRequest        = SdesRequest(Seq.empty, expectedMetadata, expectedMetadataProperties, attachmentElementContents)
      val expectedServiceResult      = SdesSuccess(uuid = expectedSdesUuid)

      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SdesSuccess(expectedSdesUuid)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(expectedServiceResult)
      verify(sdesConnectorMock).postMessage(expectedSdesRequest)
      bodyCaptor hasCaptured expectedSdesRequest
    }

    "omit TaskIdentifier from SDES metadata header where blank in message" in new Setup {
      val xmlBody: Elem              = readFromFile("crdl/crdl-request-blank-task-identifer.xml")
      val expectedSdesUuid           = UUID.randomUUID().toString
      val expectedMetadata           = Map(
        "srn"             -> crdlConfig.srn,
        "informationType" -> crdlConfig.informationType,
        "filename"        -> "referencedata__1577934245.txt.gz"
      )
      val expectedMetadataProperties = Map.empty[String, String]
      val expectedSdesRequest        = SdesRequest(Seq.empty, expectedMetadata, expectedMetadataProperties, attachmentElementContents)
      val expectedServiceResult      = SdesSuccess(uuid = expectedSdesUuid)

      when(sdesConnectorMock.postMessage(bodyCaptor)(*)).thenReturn(successful(SdesSuccess(expectedSdesUuid)))

      val result = await(service.processMessage(xmlBody))

      result shouldBe List(expectedServiceResult)
      verify(sdesConnectorMock).postMessage(expectedSdesRequest)
      bodyCaptor hasCaptured expectedSdesRequest
    }
  }
}

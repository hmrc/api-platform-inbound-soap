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
import scala.xml.{Elem, NodeSeq}

import org.apache.pekko.stream.Materializer
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.xmlunit.builder.DiffBuilder.compare
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors.byName

import play.api.http.Status
import play.api.http.Status.{IM_A_TEAPOT, OK, SERVICE_UNAVAILABLE, UNPROCESSABLE_ENTITY}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.CrdlOrchestratorConnector
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.xml.{CrdlAttachmentReplacingTransformer, NoChangeTransformer, XmlTransformer}

class InboundCrdlMessageServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  val forwardedHeadersWithAttachment = Seq[(String, String)]("x-files-included" -> "true")
  val forwardedHeadersNoAttachment   = Seq[(String, String)]("x-files-included" -> "false")

  val httpStatus: Int = Status.OK

  def readFromFile(fileName: String) = {
    xml.XML.load(Source.fromResource(fileName).bufferedReader())
  }

  trait Setup {
    val crdlSdesServiceMock: CrdlSdesService                     = mock[CrdlSdesService]
    val crdlOrchestratorConnectorMock: CrdlOrchestratorConnector = mock[CrdlOrchestratorConnector]
    val workingXmlTransformer: XmlTransformer                    = new CrdlAttachmentReplacingTransformer()
    val failingXmlTransformer: XmlTransformer                    = new NoChangeTransformer()
    val forwardedMessageCaptor                                   = ArgCaptor[NodeSeq]
    val wholeMessageCaptor                                       = ArgCaptor[NodeSeq]
    val binaryElementsCaptor                                     = ArgCaptor[NodeSeq]
    val headerCaptor                                             = ArgCaptor[Seq[(String, String)]]
    val sdesRequestHeaderCaptor                                  = ArgCaptor[Seq[(String, String)]]
    val xmlBodyWithAttachment                                    = readFromFile("crdl/crdl-request-well-formed.xml")
    val xmlBodyNoAttachment                                      = readFromFile("crdl/crdl-request-no-attachment.xml")

    val service: InboundCrdlMessageService =
      new InboundCrdlMessageService(crdlOrchestratorConnectorMock, crdlSdesServiceMock, workingXmlTransformer)

    val serviceForError: InboundCrdlMessageService =
      new InboundCrdlMessageService(crdlOrchestratorConnectorMock, crdlSdesServiceMock, failingXmlTransformer)
  }

  private def getXmlDiff(actual: NodeSeq, expected: Elem): DiffBuilder = {
    compare(Input.fromString(expected.toString).build())
      .withTest(Input.fromString(actual.toString()).build())
      .withNodeMatcher(new DefaultNodeMatcher(byName))
      .checkForIdentical()
  }

  "processInboundMessage" should {
    "return success when connector returns success" in new Setup {
      when(crdlOrchestratorConnectorMock.postMessage(forwardedMessageCaptor, headerCaptor)(*)).thenReturn(successful(SendSuccess(OK)))

      val result = await(service.processInboundMessage(xmlBodyNoAttachment))

      result shouldBe SendSuccess(OK)
      verify(crdlOrchestratorConnectorMock).postMessage(xmlBodyNoAttachment, forwardedHeadersNoAttachment)
      forwardedMessageCaptor hasCaptured xmlBodyNoAttachment
      headerCaptor hasCaptured forwardedHeadersNoAttachment
    }

    "invoke SDESConnector when message contains embedded file attachment" in new Setup {
      val forwardedXmlBody = readFromFile("post-sdes-processing/crdl/crdl-request-well-formed.xml")

      when(crdlOrchestratorConnectorMock.postMessage(forwardedMessageCaptor, headerCaptor)(*)).thenReturn(successful(SendSuccess(OK)))
      when(crdlSdesServiceMock.processMessage(refEq(xmlBodyWithAttachment))(*)).thenReturn(successful(List(SdesSuccess(
        "some-uuid-like-string"
      ))))
      val result = await(service.processInboundMessage(xmlBodyWithAttachment))

      result shouldBe SendSuccess(OK)
      verify(crdlOrchestratorConnectorMock).postMessage(forwardedMessageCaptor, headerCaptor)(*)
      verify(crdlSdesServiceMock).processMessage(xmlBodyWithAttachment)
      getXmlDiff(forwardedMessageCaptor.value, forwardedXmlBody).build().hasDifferences mustBe false
      headerCaptor.value mustBe forwardedHeadersWithAttachment
    }

    "return fail status to caller and not forward message if call to SDES fails when processing a message with embedded file" in new Setup {
      when(crdlSdesServiceMock.processMessage(forwardedMessageCaptor)(*)).thenReturn(successful(List(
        SendFailExternal("some error", SERVICE_UNAVAILABLE)
      )))

      val result = await(service.processInboundMessage(xmlBodyWithAttachment))

      result shouldBe SendFailExternal("some error", SERVICE_UNAVAILABLE)
      verifyZeroInteractions(crdlOrchestratorConnectorMock)
    }

    "return fail status to caller and not forward message if attempt to extract embedded file fails" in new Setup {
      when(crdlSdesServiceMock.processMessage(forwardedMessageCaptor)(*)).thenReturn(successful(List(
        SendNotAttempted("some error")
      )))

      val result = await(serviceForError.processInboundMessage(xmlBodyWithAttachment))

      result shouldBe SendNotAttempted("some error")
      verifyZeroInteractions(crdlOrchestratorConnectorMock)
    }

    "return fail status to caller and not forward message if attempt to replace embedded file with SDES UUID fails" in new Setup {
      when(crdlSdesServiceMock.processMessage(forwardedMessageCaptor)(*)).thenReturn(successful(List(
        SdesSuccess("some-uuid")
      )))

      val result = await(serviceForError.processInboundMessage(xmlBodyWithAttachment))

      result shouldBe SendFailExternal(s"Failed to replace embedded attachment for $xmlBodyWithAttachment", UNPROCESSABLE_ENTITY)
      verifyZeroInteractions(crdlOrchestratorConnectorMock)
    }

    "return fail status to caller and not forward message if message attachment is blank or absent" in new Setup {
      when(crdlSdesServiceMock.processMessage(forwardedMessageCaptor)(*)).thenReturn(successful(List(
        SendNotAttempted("some error")
      )))

      val result = await(service.processInboundMessage(xmlBodyWithAttachment))

      result shouldBe SendNotAttempted("some error")
      verifyZeroInteractions(crdlOrchestratorConnectorMock)
    }

    "return failure when attempt to forward message fails" in new Setup {
      when(crdlOrchestratorConnectorMock.postMessage(forwardedMessageCaptor, headerCaptor)(*)).thenReturn(successful(SendFailExternal("some error", IM_A_TEAPOT)))

      val result = await(service.processInboundMessage(xmlBodyNoAttachment))

      result shouldBe SendFailExternal("some error", IM_A_TEAPOT)
      verify(crdlOrchestratorConnectorMock).postMessage(xmlBodyNoAttachment, forwardedHeadersNoAttachment)
      forwardedMessageCaptor hasCaptured xmlBodyNoAttachment
    }
  }
}

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

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.{sequence, successful}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.util.{Base64Encoder, CertexUuidHelper, UuidGenerator}
import uk.gov.hmrc.apiplatforminboundsoap.xml.CertexXml

@Singleton
class CertexSdesService @Inject() (
    appConfig: SdesConnector.Config,
    sdesConnector: SdesConnector,
    override val uuidGenerator: UuidGenerator
  )(implicit executionContext: ExecutionContext
  ) extends CertexXml with CertexUuidHelper with Base64Encoder with MessageService {

  override def getAttachment(wholeMessage: NodeSeq): Either[InvalidFormatResult, String] = {
    getBinaryAttachment(wholeMessage) match {
      case attachment: NodeSeq if attachment.text.isBlank    =>
        Left(InvalidFormatResult("Embedded attachment element pcaDocumentPdf is empty"))
      case attachment: NodeSeq if !isBase64(attachment.text) =>
        Left(InvalidFormatResult("Embedded attachment element pcaDocumentPdf is not valid base 64 data"))
      case attachment: NodeSeq                               =>
        Right(attachment.text)
    }
  }

  override def buildSdesRequest(wholeMessage: NodeSeq, attachmentElement: NodeSeq): Either[InvalidFormatResult, SdesRequest] = {
    getAttachment(wholeMessage) match {
      case Right(attachment) =>
        Right(SdesRequest(
          body = attachment,
          headers = Seq.empty,
          metadata = buildMetadata(wholeMessage),
          metadataProperties = buildMetadataProperties(wholeMessage, attachmentElement)
        ))
      case Left(result)      => Left(result)
    }
  }

  override def processMessage(wholeMessage: NodeSeq)(implicit hc: HeaderCarrier): Future[Seq[SendResult]] = {
    val attachment = getBinaryAttachment(wholeMessage)
    sequence(attachment.map(attachmentElement => {
      buildSdesRequest(wholeMessage, attachmentElement) match {
        case Right(sdesRequest)           => sdesConnector.postMessage(sdesRequest) flatMap {
            case s: SdesSuccess      =>
              successful(SdesSuccess(uuid = s.uuid))
            case f: SendFailExternal =>
              logger.warn(s"${f.status} returned from SDES call due to ${f.message}")
              successful(SendFailExternal(s"${f.status} returned from SDES call due to ${f.message}", f.status))
          }
        case Left(e: InvalidFormatResult) =>
          logger.warn(s"${e.reason}")
          successful(SendNotAttempted(e.reason))
      }
    }))
  }

  override def buildMetadata(attachmentElement: NodeSeq): Map[String, String] = {
    def getUuidFromMessageId(messageId: String): String = {
      uuidFromMessageId(messageId) match {
        case Right(msgId)     => msgId
        case Left(randomUuid) =>
          logger.warn(s"UUID included in `filename` metadata property was randomly generated for SDES request")
          randomUuid
      }
    }
    def fileName(): String                              = {
      getMessageId(attachmentElement) match {
        case Some(m) => s"certex_${getUuidFromMessageId(m)}.pdf"
        case None    =>
          logger.warn(s"Attribute messageId not found in message so generating random UUID for SDES filename")
          s"certex_${uuidGenerator.generateRandomUuid}.pdf"

      }
    }
    Map(
      "srn" -> appConfig.certex.srn,
      "informationType" -> appConfig.certex.informationType,
      "filename"        -> fileName()
    )
  }

  override def buildMetadataProperties(wholeMessage: NodeSeq, attachmentElement: NodeSeq): Map[String, String] = {
    Map[String, String]("MRN" -> getMrn(wholeMessage), "documentSource" -> "certex") ++ getMessageId(wholeMessage).map(m => "messageId" -> m)
  }
}

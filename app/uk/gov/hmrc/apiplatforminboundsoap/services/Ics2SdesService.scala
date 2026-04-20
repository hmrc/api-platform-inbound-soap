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
import scala.concurrent.Future.{failed, sequence, successful}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.{SdesSendFailExternal, SdesSendNotAttempted, SdesSendResult, SdesSuccess, SdesSuccessResult}
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

@Singleton
class Ics2SdesService @Inject() (appConfig: SdesConnector.Config, sdesConnector: SdesConnector)(implicit executionContext: ExecutionContext)
    extends MessageService with Ics2XmlHelper {

  private def getAttachmentElements(wholeMessage: NodeSeq): NodeSeq = getBinaryElementsWithEmbeddedData(wholeMessage)

  override def getAttachment(attachmentElement: NodeSeq): Either[InvalidFormatResult, String] = {
    (getBinaryFilename(attachmentElement), getBinaryBase64Object(attachmentElement)) match {
      case (Some(filename), Some(_)) if filename.isEmpty => Left(InvalidFormatResult("Argument filename found in XML but is empty"))
      case (Some(_), Some(binaryAttachment))             => Right(binaryAttachment)
      case (None, Some(_))                               => Left(InvalidFormatResult("Argument filename was not found in XML"))
      case (_, None)                                     => Left(InvalidFormatResult("Argument includedBinaryObject was not found in XML"))
    }
  }

  override def processMessage(wholeMessage: NodeSeq)(implicit hc: HeaderCarrier): Future[Seq[SdesSendResult]] = {
    val attachments = getAttachmentElements(wholeMessage)
    sequence(attachments.map(attachmentElement => {
      buildSdesRequest(wholeMessage, attachmentElement) match {
        case Right(sdesRequest)           => sdesConnector.postMessage(sdesRequest) flatMap {
            case s: SdesSuccess                                 => getBinaryFilename(attachmentElement) match {
                case Some(filename) =>
                  successful(SdesSuccessResult(SdesReference(uuid = s.uuid, forFilename = filename)))
                case None           => ??? // TODO What do we do if the filename isn't found?
              }
            case f: SdesSendFailExternal                        =>
              successful(SdesSendFailExternal(s"${f.status} returned from SDES call", f.status))
            case SdesSendNotAttempted(_) | SdesSuccessResult(_) => // TODO Does this make sense in this context?
              failed(new UnsupportedOperationException("Unexpected return type from call to postMessage"))
          }
        case Left(e: InvalidFormatResult) =>
          logger.warn(s"${e.reason}")
          successful(SdesSendNotAttempted(e.reason))
      }
    }))
  }

  override def buildMetadata(attachmentElement: NodeSeq): Map[String, String] = {
    val fileName = getBinaryFilename(attachmentElement)

    Map(
      "srn"             -> Some(appConfig.ics2.srn),
      "informationType" -> Some(appConfig.ics2.informationType),
      "filename"        -> fileName
    ).collect(filterEmpty)
  }

  override def buildMetadataProperties(wholeMessage: NodeSeq, attachmentElement: NodeSeq): Map[String, String] = {

    val description              = getBinaryDescription(attachmentElement)
    val mimeType                 = getBinaryMimeType(attachmentElement)
    val messageId                = getMessageId(wholeMessage)
    val referralRequestReference = getReferralRequestReference(wholeMessage)
    val mrn                      = getMRN(wholeMessage)
    val lrn                      = getLRN(wholeMessage)

    Map(
      "referralRequestReference" -> referralRequestReference,
      "messageId"                -> messageId,
      "description"              -> description,
      "fileMIME"                 -> mimeType,
      "MRN"                      -> mrn,
      "LRN"                      -> lrn
    ).collect(filterEmpty)
  }

}

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

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

@Singleton
class Ics2SdesService @Inject() (appConfig: AppConfig, sdesConnector: SdesConnector)(implicit executionContext: ExecutionContext) extends Ics2XmlHelper {

  def processMessage(binaryElements: NodeSeq)(implicit hc: HeaderCarrier): Future[Seq[SendResult]] = {
    val allAttachments = getBinaryElementsWithEmbeddedData(binaryElements)
    sequence(allAttachments.map(attachmentElement => {
      buildSdesRequest(binaryElements, attachmentElement) match {
        case Right(sdesRequest)           => sdesConnector.postMessage(sdesRequest) flatMap {
            case s: SdesSuccess      => getBinaryFilename(attachmentElement) match {
                case Some(filename) =>
                  successful(SdesSuccessResult(SdesReference(uuid = s.uuid, forFilename = filename)))
              }
            case f: SendFailExternal =>
              logger.warn(s"${f.status} returned from SDES call")
              successful(SendFailExternal(f.status))
          }
        case Left(e: InvalidFormatResult) =>
          logger.warn(s"${e.reason}")
          successful(SendNotAttempted(e.reason))
      }
    }))
  }

  private def buildSdesRequest(soapRequest: NodeSeq, attachmentElement: NodeSeq) = {
    def getAttachment(soapRequest: NodeSeq)                                                  = {
      (getBinaryFilename(attachmentElement), getBinaryBase64Object(soapRequest)) match {
        case (Some(filename), Some(_)) if filename.isEmpty => Left(InvalidFormatResult("Argument filename found in XML but is empty"))
        case (Some(_), Some(binaryAttachment))             => Right(binaryAttachment)
        case (None, Some(_))                               => Left(InvalidFormatResult("Argument filename was not found in XML"))
        case (_, None)                                     => Left(InvalidFormatResult("Argument includedBinaryObject was not found in XML"))
      }
    }
    val filterEmpty: PartialFunction[(String, Option[String]), (String, String)]             = {
      case v if v._2.nonEmpty => (v._1, v._2.get)
    }
    def buildMetadata(soapRequest: NodeSeq, attachmentElement: NodeSeq): Map[String, String] = {
      val fileName = getBinaryFilename(attachmentElement)

      Map(
        "srn"             -> Some(appConfig.ics2SdesSrn),
        "informationType" -> Some(appConfig.ics2SdesInfoType),
        "filename"        -> fileName
      ).collect(filterEmpty)
    }

    def buildMetadataProperties(soapRequest: NodeSeq, attachmentElement: NodeSeq): Map[String, String] = {

      val description              = getBinaryDescription(attachmentElement)
      val mimeType                 = getBinaryMimeType(attachmentElement)
      val messageId                = getMessageId(soapRequest)
      val referralRequestReference = getReferralRequestReference(soapRequest)
      val mrn                      = getMRN(soapRequest)
      val lrn                      = getLRN(soapRequest)

      Map(
        "referralRequestReference" -> referralRequestReference,
        "messageId"                -> messageId,
        "description"              -> description,
        "fileMIME"                 -> mimeType,
        "MRN"                      -> mrn,
        "LRN"                      -> lrn
      ).collect(filterEmpty)
    }

    getAttachment(attachmentElement) match {
      case Right(attachment) =>
        Right(SdesRequest(
          body = attachment,
          headers = Seq.empty,
          metadata = buildMetadata(soapRequest, attachmentElement),
          metadataProperties = buildMetadataProperties(soapRequest, attachmentElement)
        ))
      case Left(result)      => Left(result)
    }
  }
}

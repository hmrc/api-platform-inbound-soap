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
    println(s"allAttachments is $allAttachments")
    sequence(allAttachments.map(attachmentElement => {
      buildSdesRequest(binaryElements, attachmentElement) match {
        case Right(sdesRequest)           => sdesConnector.postMessage(sdesRequest) flatMap {
            case s: SdesSendSuccess =>
              successful(SdesSendSuccessResult(SdesResult(uuid = s.uuid, forFilename = getBinaryFilename(attachmentElement))))
            case f: SendFail        =>
              logger.warn(s"${f.status} returned from SDES call")
              successful(SendFail(f.status))
          }
        case Left(e: InvalidFormatResult) =>
          logger.warn(s"${e.reason}")
          successful(SendNotAttempted(e.reason))
      }
    }))
  }

  private def buildSdesRequest(soapRequest: NodeSeq, attachmentElement: NodeSeq) = {
    def getAttachment(soapRequest: NodeSeq)                                                  = {
      getBinaryBase64Object(soapRequest) match {
        case Some(binaryAttachment) => Right(binaryAttachment)
        case _                      => Left(InvalidFormatResult("Argument includedBinaryObject is not valid base 64 data"))
      }
    }
    def buildMetadata(soapRequest: NodeSeq, attachmentElement: NodeSeq): Map[String, String] = {
      val description              = getBinaryDescription(attachmentElement)
      val mimeType                 = getBinaryMimeType(attachmentElement)
      val fileName                 = getBinaryFilename(attachmentElement)
      val referralRequestReference = getReferralRequestReference(soapRequest)
      val mrn                      = getMRN(soapRequest)
      val lrn                      = getLRN(soapRequest)
      Map(
        "srn"                      -> Some(appConfig.ics2SdesSrn),
        "informationType"          -> Some(appConfig.ics2SdesInfoType),
        "filename"                 -> fileName,
        "description"              -> description,
        "fileMIME"                 -> mimeType,
        "MRN"                      -> mrn,
        "LRN"                      -> lrn,
        "referralRequestReference" -> referralRequestReference
      )
        .filterNot(elem => elem._2.isEmpty)
        .map(k => (k._1, k._2.get))
    }

    getAttachment(attachmentElement) match {
      case Right(attachment) =>
        Right(SdesRequest(body = attachment, headers = Seq.empty, metadata = buildMetadata(soapRequest, attachmentElement), destinationUrl = appConfig.sdesUrl))
      case Left(result)      => Left(result)
    }
  }
}

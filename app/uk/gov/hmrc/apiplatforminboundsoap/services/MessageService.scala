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

import javax.inject.Singleton
import scala.concurrent.Future
import scala.util.Either
import scala.xml.NodeSeq

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.models._

@Singleton
trait MessageService {

  val filterEmpty: PartialFunction[(String, Option[String]), (String, String)] = {
    case v if v._2.nonEmpty => (v._1, v._2.get)
  }

  def buildMetadata(attachmentElement: NodeSeq): Map[String, String]

  def buildMetadataProperties(wholeMessage: NodeSeq, attachmentElement: NodeSeq): Map[String, String]

  def processMessage(wholeMessage: NodeSeq)(implicit hc: HeaderCarrier): Future[Seq[SendResult]]

  def getAttachment(attachmentElement: NodeSeq): Either[InvalidFormatResult, String]

  def buildSdesRequest(wholeMessage: NodeSeq, attachmentElement: NodeSeq): Either[InvalidFormatResult, SdesRequest] = {
    getAttachment(attachmentElement) match {
      case Right(attachment) =>
        Right(SdesRequest(
          body = attachment,
          headers = Seq.empty,
          metadata = buildMetadata(attachmentElement),
          metadataProperties = buildMetadataProperties(wholeMessage, attachmentElement)
        ))
      case Left(result)      => Left(result)
    }
  }
}

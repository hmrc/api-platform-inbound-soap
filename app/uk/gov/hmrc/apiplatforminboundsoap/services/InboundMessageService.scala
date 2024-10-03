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
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

import org.apache.pekko.http.scaladsl.util.FastFuture.successful

import play.api.http.Status.UNPROCESSABLE_ENTITY
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.connectors.InboundConnector
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

@Singleton
class InboundMessageService @Inject() (appConfig: AppConfig, inboundConnector: InboundConnector, sdesService: Ics2SdesService)(implicit ec: ExecutionContext)
    extends Ics2XmlHelper {

  def processInboundMessage(wholeMessage: NodeSeq, isTest: Boolean = false)(implicit hc: HeaderCarrier): Future[SendResult] = {
    val forwardUrl                        = if (isTest) appConfig.testForwardMessageUrl else appConfig.forwardMessageUrl
    val newHeaders: Seq[(String, String)] = buildHeadersToAppend(wholeMessage)
    val allAttachments                    = getBinaryElementsWithEmbeddedData(wholeMessage)
    if (isFileIncluded(wholeMessage) && allAttachments.nonEmpty) {
      sendToSdes(wholeMessage, allAttachments, forwardUrl)
    } else {
      forwardMessageOnwards(SoapRequest(wholeMessage.toString, forwardUrl), newHeaders)
    }
  }

  private def buildHeadersToAppend(soapRequest: NodeSeq): Seq[(String, String)] = {
    List(
      "x-soap-action"    -> getSoapAction(soapRequest).getOrElse(""),
      "x-correlation-id" -> getMessageId(soapRequest).getOrElse(""),
      "x-message-id"     -> getMessageId(soapRequest).getOrElse(""),
      "x-files-included" -> isFileIncluded(soapRequest).toString,
      "x-version-id"     -> getMessageVersion(soapRequest).displayName
    )
  }

  private def sendToSdes(wholeMessage: NodeSeq, binaryElements: NodeSeq, forwardUrl: String)(implicit hc: HeaderCarrier): Future[SendResult] = {
    sdesService.processMessage(binaryElements) flatMap {
      sendResults: Seq[SendResult] =>
        sendResults.find(r => r.isInstanceOf[SendFail]) match {
          case Some(value) => successful(value)
          case None        => processSdesResults(sendResults.asInstanceOf[Seq[SdesSuccessResult]], wholeMessage) match {
              case Right(xml) => forwardMessageOnwards(SoapRequest(xml.toString(), forwardUrl), buildHeadersToAppend(wholeMessage))
              case Left(f)    =>
                logger.warn(s"Failed to replace all embedded attachments for files $f")
                successful(SendFailExternal(UNPROCESSABLE_ENTITY))
            }
        }
    }
  }

  private def processSdesResults(sdesResults: Seq[SdesSuccessResult], wholeMessage: NodeSeq): Either[Set[String], NodeSeq] = {
    val replacements = sdesResults.map(sr => (sr.sdesReference.forFilename, sr.sdesReference.uuid))
    replaceEmbeddedAttachments(replacements.toMap[String, String], wholeMessage)
  }

  private def forwardMessageOnwards(soapRequest: SoapRequest, newHeaders: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[SendResult] = {
    inboundConnector.postMessage(soapRequest, newHeaders)
  }
}

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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

import com.google.inject.name.Named

import play.api.http.Status.UNPROCESSABLE_ENTITY
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.CrdlOrchestratorConnector
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.{SdesSendFail, SdesSendResult, SdesSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger
import uk.gov.hmrc.apiplatforminboundsoap.xml.{CrdlXml, XmlTransformer}

@Singleton
class InboundCrdlMessageService @Inject() (
    crdlOrchestratorConnector: CrdlOrchestratorConnector,
    sdesService: CrdlSdesService,
    @Named("crdl") override val xmlTransformer: XmlTransformer
  )(implicit ec: ExecutionContext
  ) extends ApplicationLogger with CrdlXml with SdesResultMapper {

  def processInboundMessage(wholeMessage: NodeSeq)(implicit hc: HeaderCarrier): Future[SendResult] = {
    val extraHeaders: Seq[(String, String)] = buildHeadersToAppend(wholeMessage)

    if (fileIncluded(wholeMessage)) {
      sendToSdesThenForwardMessage(wholeMessage, extraHeaders)
    } else {
      forwardMessage(wholeMessage, extraHeaders)
    }
  }

  private def sendToSdesThenForwardMessage(wholeMessage: NodeSeq, extraHeaders: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[SendResult] = {
    sdesService.processMessage(wholeMessage) flatMap {
      sendResults: List[Either[SdesSendFail,SdesSendResult]] =>
        val  lefts =  sendResults.find(_.isLeft)
          sendResults.find(_.isLeft).
        if (lefts.nonEmpty){
          successful(mapFailedSdesSendResultToSendResult(lefts.head.left.get))



        val result: Boolean = sendResults.forall(r=>r.isRight)
        val results: Seq[Seq[SdesSendResult]] = sendResults.dropWhile(r=>r.isRight).map(l=>l.toSeq)
        val results2: List[Object] = sendResults.filter(r=>r.isLeft).map(s => s.left.getOrElse(None))
        val rights: List[SdesSuccess] = sendResults.takeWhile(r=>r.isRight).asInstanceOf[List[SdesSuccess]]
         sendResults.partition(r => r.isLeft) match {
           case (Nil, r) => processSdesResults(wholeMessage, r.asInstanceOf[List[SdesSuccess]]) match {
             case Right(xml) => forwardMessage(xml, extraHeaders)
             case Left(_)    =>
               logger.warn(s"Failed to replace embedded attachment for $wholeMessage")
               successful(SendFailExternal(s"Failed to replace embedded attachment for $wholeMessage", UNPROCESSABLE_ENTITY))
           }
//           case (r, _) => successful(mapFailedSdesSendResultToSendResult(r.hea))
         }

//        sendResults.find(r => r.isInstanceOf[SdesSendFail]) match {
          case Some(value: SdesSendFail) => successful(mapFailedSdesSendResultToSendResult(value))
          case Some(value)               => {
            logger.warn(s"Found a non failure send result when expecting a failure - $value")
            successful(UnexpectedSendFailure)
          }
          case None                      => processSdesResults(wholeMessage, sendResults.asInstanceOf[List[SdesSuccess]]) match {
              case Right(xml) => forwardMessage(xml, extraHeaders)
              case Left(_)    =>
                logger.warn(s"Failed to replace embedded attachment for $wholeMessage")
                successful(SendFailExternal(s"Failed to replace embedded attachment for $wholeMessage", UNPROCESSABLE_ENTITY))
            }
        }
    }
  }

  private def buildHeadersToAppend(soapRequest: NodeSeq): Seq[(String, String)] = {
    List(
      "x-files-included" -> fileIncluded(soapRequest).toString
    )
  }

  private def processSdesResults(wholeMessage: NodeSeq, sdesResult: List[SdesSuccess]): Either[Unit, NodeSeq] = {
    val messageWithAttachmentReplaced = xmlTransformer.replaceAttachment(wholeMessage, sdesResult.head.uuid)
    if (messageWithAttachmentReplaced != wholeMessage) Right(messageWithAttachmentReplaced) else Left(())
  }

  private def forwardMessage(soapRequest: NodeSeq, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[SendResult] = {
    crdlOrchestratorConnector.postMessage(soapRequest, headers)
  }
}

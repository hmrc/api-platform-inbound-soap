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

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.connectors.InboundConnector
import uk.gov.hmrc.apiplatforminboundsoap.models.{SdesSendSuccessResult, SendFail, SendResult, SoapRequest}
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
    logger.info("found embedded file")
    sdesService.processMessage(binaryElements) flatMap {
      sendResults: Seq[SendResult] =>
        println(s"sendResults is $sendResults")
        val sendFail = sendResults.find(r => r.isInstanceOf[SendFail])
        if (sendFail.nonEmpty) {
          logger.warn("one failed")
          successful(sendFail.get)
        } else {
//          for {
//            updatedXml <- processSdesResults(sendResults.asInstanceOf[Seq[SdesSendSuccessResult]], binaryElements, forwardUrl)
//           res <- forwardMessageOnwards (SoapRequest(updatedXml.toString(), forwardUrl), buildHeadersToAppend(binaryElements))
//          } yield res
          val updatedXml = processSdesResults(sendResults.asInstanceOf[Seq[SdesSendSuccessResult]], binaryElements, forwardUrl)
          forwardMessageOnwards(SoapRequest(wholeMessage.toString(), forwardUrl), buildHeadersToAppend(binaryElements))
        }
    }
  }

  //  private def processSdesResults(sdesResults: Seq[SdesSendSuccessResult]): SendResult = {
  private def processSdesResults(sdesResults: Seq[SdesSendSuccessResult], nodeSeq: NodeSeq, forwardUrl: String): NodeSeq = {
    sdesResults.map(println(_))
    nodeSeq
    //    forwardMessageOnwards(SoapRequest(nodeSeq.toString(), forwardUrl), buildHeadersToAppend(nodeSeq))
    //   SendSuccess
  }

  private def forwardMessageOnwards(soapRequest: SoapRequest, newHeaders: Seq[(String, String)])(implicit hc: HeaderCarrier) = {
    inboundConnector.postMessage(soapRequest, newHeaders)
  }
}

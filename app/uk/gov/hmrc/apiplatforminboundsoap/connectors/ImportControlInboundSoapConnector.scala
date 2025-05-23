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

package uk.gov.hmrc.apiplatforminboundsoap.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.xml.NodeSeq

import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendResult, SendSuccess}

object ImportControlInboundSoapConnector {
  case class Config(baseUrl: String, testForwardMessageUrl: String)
}

@Singleton
class ImportControlInboundSoapConnector @Inject() (httpClientV2: HttpClientV2, appConfig: ImportControlInboundSoapConnector.Config)(implicit ec: ExecutionContext)
    extends BaseConnector(httpClientV2) with Logging {

  def postMessage(soapRequest: NodeSeq, headers: Seq[(String, String)], isTest: Boolean)(implicit hc: HeaderCarrier): Future[SendResult] = {
    val forwardUrl = if (isTest) appConfig.testForwardMessageUrl else s"${appConfig.baseUrl}/import-control-inbound-soap"

    postHttpRequest(soapRequest, headers, forwardUrl).map {
      case Left(UpstreamErrorResponse(_, statusCode, _, _)) =>
        logger.warn(s"Sending message failed with status code $statusCode")
        SendFailExternal(statusCode)
      case Right(httpResponse: HttpResponse)                =>
        SendSuccess(httpResponse.status)
    }
      .recoverWith {
        case NonFatal(e) =>
          logger.warn(s"NonFatal error ${e.getMessage} while forwarding message", e)
          Future.successful(SendFailExternal(Status.INTERNAL_SERVER_ERROR))
      }
  }
}

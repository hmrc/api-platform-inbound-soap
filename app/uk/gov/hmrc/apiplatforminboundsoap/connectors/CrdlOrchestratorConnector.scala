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

import play.api.http.Status
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendResult, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

object CrdlOrchestratorConnector {
  case class Config(baseUrl: String, path: String)
}

@Singleton
class CrdlOrchestratorConnector @Inject() (httpClientV2: HttpClientV2, appConfig: CrdlOrchestratorConnector.Config)(implicit ec: ExecutionContext)
    extends BaseConnector(httpClientV2) with ApplicationLogger {

  def postMessage(soapRequest: NodeSeq, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[SendResult] = {
    postHttpRequest(soapRequest, headers, s"${appConfig.baseUrl}${appConfig.path}").map {

      case Right(response)                                        => SendSuccess(response.status)
      case Left(UpstreamErrorResponse(message, statusCode, _, _)) =>
        logger.warn(s"Sending message failed with status code $statusCode: $message")
        SendFailExternal(message, statusCode)
    }
      .recoverWith {
        case NonFatal(e) =>
          logger.warn(s"NonFatal error ${e.getMessage} while forwarding message in CrdlOrchestratorConnector.postMessage", e)
          Future.successful(SendFailExternal(e.getMessage, Status.INTERNAL_SERVER_ERROR))
      }
  }
}

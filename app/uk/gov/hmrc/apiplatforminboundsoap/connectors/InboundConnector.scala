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

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendResult, SendSuccess, SoapRequest}

@Singleton
class InboundConnector @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) extends Logging {

  def postMessage(soapRequest: SoapRequest, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[SendResult] = {
    postHttpRequest(soapRequest, headers).map {
      case Left(UpstreamErrorResponse(_, statusCode, _, _)) =>
        logger.warn(s"Sending message failed with status code $statusCode")
        SendFail(statusCode)
      case Right(response: HttpResponse)                    =>
        SendSuccess
    }
      .recoverWith {
        case NonFatal(e) =>
          logger.warn(s"NonFatal error ${e.getMessage} while forwarding message", e)
          Future.successful(SendFail(Status.INTERNAL_SERVER_ERROR))
      }
  }

  private def postHttpRequest(soapRequest: SoapRequest, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    val httpClient = httpClientV2.post(new URL(soapRequest.destinationUrl)).withBody(soapRequest.soapEnvelope).transform(_.addHttpHeaders(headers: _*))
    if (appConfig.proxyRequired) {
      httpClient.withProxy.execute[Either[UpstreamErrorResponse, HttpResponse]]
    } else {
      httpClient.execute[Either[UpstreamErrorResponse, HttpResponse]]
    }
  }
}

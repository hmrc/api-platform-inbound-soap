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
import uk.gov.hmrc.apiplatforminboundsoap.xml.Ics2XmlHelper

object ApiPlatformOutboundSoapConnector {
  case class Config(baseUrl: String)
}

@Singleton
class ApiPlatformOutboundSoapConnector @Inject() (httpClientV2: HttpClientV2, appConfig: ApiPlatformOutboundSoapConnector.Config)(implicit ec: ExecutionContext)
    extends BaseConnector(httpClientV2) with ApplicationLogger with Ics2XmlHelper {

  def postMessage(soapRequest: NodeSeq)(implicit hc: HeaderCarrier): Future[SendResult] = {

    val newHeaders: Seq[(String, String)] = List("x-soap-action" -> getSoapAction(soapRequest).getOrElse(""))

    postHttpRequest(soapRequest, newHeaders, appConfig.baseUrl).map {
      case Right(_)                                         => SendSuccess
      case Left(UpstreamErrorResponse(_, statusCode, _, _)) =>
        logger.warn(s"Sending message failed with status code $statusCode")
        SendFailExternal(statusCode)
    }
      .recoverWith {
        case NonFatal(e) =>
          logger.warn(s"NonFatal error ${e.getMessage} while forwarding message", e)
          Future.successful(SendFailExternal(Status.INTERNAL_SERVER_ERROR))
      }
  }
}

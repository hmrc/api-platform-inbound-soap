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
import scala.concurrent.Future
import scala.xml.NodeSeq

import play.api.Logging
import play.api.mvc.Headers
import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.connectors.InboundConnector
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendResult, SoapRequest}
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class InboundMessageService @Inject() (appConfig: AppConfig, inboundConnector: InboundConnector) extends Logging {

  def processInboundMessage(soapRequest: NodeSeq, headers: Headers)(implicit hc: HeaderCarrier): Future[SendResult] = {
    val existingHeaders     = headers
    val newHeaders: Headers = existingHeaders
      .remove("server", "x-envoy-upstream-service-time")
      .add("x-soap-action" -> "action from message", "x-correlation-id" -> "x-correlation-id-value", "x-message-id" -> "x-message-id-value")
    inboundConnector.postMessage(SoapRequest(soapRequest.text, appConfig.forwardMessageUrl), newHeaders)
  }
}
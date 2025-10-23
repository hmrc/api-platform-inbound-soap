/*
 * Copyright 2024 HM Revenue & Customs
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

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

abstract class BaseConnector(httpClientV2: HttpClientV2)(implicit ec: ExecutionContext) {

  def postHttpRequest(soapRequest: NodeSeq, headers: Seq[(String, String)], forwardUrl: String)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    val httpClient = headers.filter(h => h._1 == "Authorization") match {
      // if we have to add an Authorization header before forwarding we replace the one on the request here
      case h: List[(String, String)] if h.nonEmpty =>
        httpClientV2.post(new URI(forwardUrl).toURL)(hc)
          .setHeader("Authorization" -> h.head._2)
          .transform(_.addHttpHeaders(headers.filterNot(h => h._1 == "Authorization"): _*))
// forward the request without the received Authorization header
      case _                                       => httpClientV2.post(new URI(forwardUrl).toURL)(hc.copy(authorization = None))
          .transform(_.addHttpHeaders(headers: _*))
    }
    httpClient.withBody(soapRequest)
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }
}

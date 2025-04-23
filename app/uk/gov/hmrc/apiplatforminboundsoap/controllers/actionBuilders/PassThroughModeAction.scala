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

package uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.xml.NodeSeq

import _root_.uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, HttpResponse, StringContextOps}
import org.apache.pekko.util.ByteString

import play.api.Logging
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.{ContentTypes, HttpEntity}
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, ResponseHeader, Result}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig

@Singleton
class PassThroughModeAction @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends ActionFilter[Request] with HttpErrorFunctions with Logging {
  override def executionContext: ExecutionContext = ec

  lazy val passThroughHost = s"${appConfig.passThroughProtocol}://${appConfig.passThroughHost}:${appConfig.passThroughPort}"

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    logger.info(s"Entering pass through filter; passthrough URL is $passThroughHost")
    val maybeAuthHeader = request.headers.headers.find(f => f._1.equalsIgnoreCase("Authorization"))
    if (appConfig.passThroughEnabled) {
      logger.info(s"In pass-through mode. Passing request on")
      val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
      postAndReturn(s"$passThroughHost${request.path}", request.body.asInstanceOf[NodeSeq], maybeAuthHeader.getOrElse(("Authorization", "")))(hc).map { httpResponse =>
        Some(Result(header = ResponseHeader(httpResponse.status, Map.empty), body = HttpEntity.Strict(ByteString(httpResponse.body), Some(ContentTypes.XML))))
      }.recoverWith {
        case NonFatal(e) =>
          logger.warn(s"Error in PassThroughModeAction.filter - ${e.getMessage} while trying to forward message", e)
          Future.successful(Some(Status(INTERNAL_SERVER_ERROR)))
      }
    } else {
      logger.info(s"Not in pass-through mode. Processing request")
      successful(None)
    }
  }

  private def postAndReturn(url: String, requestBody: NodeSeq, authHeader: (String, String))(implicit hc: HeaderCarrier) = {
    val httpClient = httpClientV2.post(url"$url").withBody(requestBody).transform(_.withHttpHeaders(authHeader))
    if (appConfig.proxyRequired) {
      httpClient.withProxy.execute[HttpResponse]
    } else {
      httpClient.execute[HttpResponse]
    }
  }
}

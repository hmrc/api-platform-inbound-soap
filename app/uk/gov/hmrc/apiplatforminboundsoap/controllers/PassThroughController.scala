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

package uk.gov.hmrc.apiplatforminboundsoap.controllers

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.xml.NodeSeq

import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

@Singleton()
class PassThroughController @Inject() (
    appConfig: AppConfig,
    httpClientV2: HttpClientV2,
    cc: ControllerComponents
  )(implicit ec: ExecutionContext
  ) extends BackendController(cc) with ApplicationLogger {

  def message(path: String): Action[AnyContent] = Action.async { implicit request =>
    val authToken = request.headers.headers.find(f => f._1.equalsIgnoreCase("Authorization"))

    def sendAndProcessResponse(path: String, nodeSeq: NodeSeq, bearerToken: (String, String)): Future[Status] = {
      postHttpRequestV2(path, nodeSeq, bearerToken: (String, String)).map {
        case Left(UpstreamErrorResponse(_, statusCode, _, _)) =>
          logger.warn(s"Sending message failed with status code $statusCode")
          Status(statusCode)
        case Right(HttpResponse(status, _, _))                =>
          Status(status)
      }.recoverWith {
        case NonFatal(e) =>
          logger.warn(s"Error in sendAndProcessResponse - ${e.getMessage} while trying to forward message", e)
          Future.successful(Status(INTERNAL_SERVER_ERROR))
      }
    }

    (request.body.asXml, authToken) match {
      case (Some(nodeSeq), Some(jwt)) => sendAndProcessResponse(path, nodeSeq, jwt)
      case (None, _)                  => Future.successful(BadRequest(s"Expected XML request body but request body was ${request.body.asText.getOrElse("empty")}"))
      case (_, None)                  => Future.successful(BadRequest("Authorization header was not supplied"))
    }
  }

  private def postHttpRequestV2(path: String, nodeSeq: NodeSeq, bearerToken: (String, String))(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    def addLeadingStrokeWhereMissing(path: String): String = {
      if (path.charAt(0).equals('/')) path else s"/$path"
    }

    def buildUrl   = {
      new URL(appConfig.forwardMessageProtocol, appConfig.forwardMessageHost, appConfig.forwardMessagePort, addLeadingStrokeWhereMissing(path))
    }
    val httpClient = httpClientV2.post(buildUrl).withBody(nodeSeq).transform(_.withHttpHeaders(bearerToken))
    if (appConfig.proxyRequired) {
      httpClient.withProxy.execute[Either[UpstreamErrorResponse, HttpResponse]]
    } else {
      httpClient.execute[Either[UpstreamErrorResponse, HttpResponse]]
    }
  }
}

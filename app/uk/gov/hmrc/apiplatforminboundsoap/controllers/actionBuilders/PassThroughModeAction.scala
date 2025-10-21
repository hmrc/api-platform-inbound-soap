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

import _root_.uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import org.apache.pekko.util.ByteString

import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.http.{ContentTypes, HttpEntity}
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, ResponseHeader, Result}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

@Singleton
class PassThroughModeAction @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends ActionFilter[Request] with ApplicationLogger {
  override def executionContext: ExecutionContext = ec
  implicit def httpReads: HttpReads[HttpResponse] = (method: String, url: String, response: HttpResponse) => HttpReads.Implicits.readRaw.read(method, url, response)
  lazy val passThroughHost                        = s"${appConfig.passThroughProtocol}://${appConfig.passThroughHost}:${appConfig.passThroughPort}"
  private val route                               = """^/(.+)/.+""".r

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    def passThroughForRoute: Either[Unit, Boolean] = {
      try {
        val route(capture) = request.path
        capture.toLowerCase match {
          case "certex" => Right(appConfig.passThroughEnabledCertex)
          case "crdl"   => Right(appConfig.passThroughEnabledCrdl)
          case "ccn2"   => Right(appConfig.passThroughEnabledAck)
          case "ics2"   => Right(appConfig.passThroughEnabledIcs2)
        }
      } catch {
        case _: MatchError =>
          logger.warn(s"Received a request on an unexpected path of ${request.path}")
          Left(())
      }
    }
    logger.info(s"Entering pass through filter; passthrough host is $passThroughHost")
    val maybeAuthHeader                            = request.headers.headers.find(f => f._1.equalsIgnoreCase("Authorization"))
    passThroughForRoute match {
      case Right(passThrough) if passThrough =>
        logger.info(s"In pass-through mode. Passing request on")
        val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        postAndReturn(s"$passThroughHost${request.path}", request.body.asInstanceOf[NodeSeq], maybeAuthHeader)(hc).map { httpResponse =>
          Some(Result(
            header = ResponseHeader(httpResponse.status, Map.empty),
            body = HttpEntity.Strict(ByteString(httpResponse.body), request.headers.get("Content-Type").orElse(Some(ContentTypes.XML)))
          ))
        }.recoverWith { case NonFatal(e) =>
          logger.warn(s"Error in PassThroughModeAction.filter - ${e.getMessage} while trying to forward message", e)
          Future.successful(Some(Status(INTERNAL_SERVER_ERROR)))
        }
      case Right(_)                          =>
        logger.info(s"Not in pass-through mode. Processing request")
        successful(None)
      case Left(_)                           =>
        Future.successful(Some(Status(NOT_FOUND)))
    }
  }

  private def postAndReturn(url: String, requestBody: NodeSeq, authHeader: Option[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    def getHttpClient: RequestBuilder = {
      authHeader match {
        case Some((h: String, v: String)) =>
          httpClientV2.post(url"$url")(hc)
            .setHeader((h, v))
            .transform(_.addHttpHeaders(hc.otherHeaders: _*))
            .withBody(requestBody)
        case None                         =>
          httpClientV2.post(url"$url")(hc)
            .transform(_.addHttpHeaders(hc.otherHeaders: _*))
            .withBody(requestBody)

      }
    }
    val httpClient                    = getHttpClient

    if (appConfig.proxyRequired) {
      httpClient.withProxy.execute[HttpResponse]
    } else {
      httpClient.execute[HttpResponse]
    }
  }
}

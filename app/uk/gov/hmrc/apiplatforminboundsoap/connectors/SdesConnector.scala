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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import play.api.Logging
import play.api.http.Status
import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatforminboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatforminboundsoap.models.{SdesRequest, SdesSuccess, SendFailExternal, SendResult}

@Singleton
class SdesConnector @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) extends Logging {
  val requiredHeaders: Seq[(String, String)] = Seq("Content-Type" -> "application/octet-stream", "User-Agent" -> "public-soap-proxy")

  def postMessage(sdesRequest: SdesRequest)(implicit hc: HeaderCarrier): Future[SendResult] = {
    postHttpRequest(sdesRequest).map {
      case Left(UpstreamErrorResponse(_, statusCode, _, _)) =>
        logger.warn(s"Sending message failed with status code $statusCode")
        SendFailExternal(statusCode)
      case Right(response: HttpResponse)                    =>
        SdesSuccess(response.body)
    }
      .recoverWith {
        case NonFatal(e) =>
          logger.warn(s"NonFatal error ${e.getMessage} while forwarding message", e)
          successful(SendFailExternal(Status.INTERNAL_SERVER_ERROR))
      }
  }

  private def postHttpRequest(sdesRequest: SdesRequest)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    val combinedHeaders = sdesRequest.headers ++ List("Metadata" -> constructMetadataHeader(sdesRequest.metadata, sdesRequest.metadataProperties))
    httpClientV2.post(new URL(appConfig.sdesUrl)).setHeader(requiredHeaders: _*)
      .withBody(sdesRequest.body)
      .transform(_.addHttpHeaders(combinedHeaders: _*))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

  private def constructMetadataHeader(metadata: Map[String, String], metadataProperties: Map[String, String]) = {
    val mp: List[JsObject] = metadataProperties.map(k => Json.obj("name" -> k._1, "value" -> k._2)).toList

    val built = {
      val builder = Json.newBuilder
      for ((k, v) <- metadata) builder ++= Seq(k -> JsString(v))
      if (mp.isEmpty) {
        builder
      } else {
        builder += "properties" -> mp
      }
    }
    Json.toJson(Json.obj("metadata" -> built.result())).toString()
  }
}

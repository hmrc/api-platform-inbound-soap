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
import scala.xml.NodeSeq

import _root_.uk.gov.hmrc.http.HttpErrorFunctions
import cats.data._

import play.api.Logging
import play.api.http.Status.BAD_REQUEST
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, Result}
import uk.gov.hmrc.apiplatforminboundsoap.xml.RequestValidator

@Singleton
class SoapMessageValidateAction @Inject() ()(implicit ec: ExecutionContext)
    extends ActionFilter[Request] with HttpErrorFunctions with Logging with RequestValidator {

  override def executionContext: ExecutionContext = ec

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {

    val body: NodeSeq = request.body.asInstanceOf[xml.NodeSeq]

    verifyElements(body) match {
      case Right(_) => successful(None)
      case Left(e)  => {
        val statusCode = BAD_REQUEST
        val requestId  = request.headers.get("x-request-id").getOrElse("requestId not known")
        logger.warn(s"RequestID: $requestId")
        logger.warn(mapErrorsToString(e, "Received a request that had a ", " that was rejected for being "))
        successful(Some(BadRequest(createSoapErrorResponse(statusCode, mapErrorsToString(e, "Argument ", " "), requestId))
          .as("application/soap+xml")))
      }
    }
  }

  private def createSoapErrorResponse(statusCode: Int, reason: String, requestId: String) = {
    s"""<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
       |    <soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
       |    <soap:Body>
       |        <soap:Fault>
       |            <soap:Code>
       |                <soap:Value>soap:$statusCode</soap:Value>
       |            </soap:Code>
       |            <soap:Reason>
       |                <soap:Text xml:lang="en">$reason</soap:Text>
       |            </soap:Reason>
       |            <soap:Node>public-soap-proxy</soap:Node>
       |            <soap:Detail>
       |                <RequestId>$requestId</RequestId>
       |            </soap:Detail>
       |        </soap:Fault>
       |    </soap:Body>
       |</soap:Envelope>""".stripMargin
  }

  private def mapErrorsToString(errorList: NonEmptyList[(String, String)], fieldName: String, problem: String): String = {
    val flatListErrors: List[(String, String)] = errorList.toList
    flatListErrors.map(problemDescription => s"$fieldName${problemDescription._1}$problem${problemDescription._2}").mkString("\n")
  }
}

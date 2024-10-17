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

package uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders

import scala.concurrent.Future.successful

import cats.data.NonEmptyList

import play.api.http.Status.BAD_REQUEST
import play.api.mvc.Results.BadRequest

import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

trait ValidateAction extends ApplicationLogger {

  def createSoapErrorResponse(statusCode: Int, reason: String, requestId: String) = {
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

  def mapErrorsToString(errorList: NonEmptyList[String], problem: String): String = {
    val flatListErrors: List[String] = errorList.toList
    flatListErrors.map(problemDescription => s"$problemDescription$problem").mkString("\n")
  }

  def returnErrorResponse(errors: NonEmptyList[String], requestId: String) = {
    val statusCode = BAD_REQUEST
    logger.warn(s"RequestID: $requestId")
    logger.warn(mapErrorsToString(errors, " caused message to be rejected"))
    successful(Some(BadRequest(createSoapErrorResponse(statusCode, mapErrorsToString(errors, ""), requestId))
      .as("application/soap+xml")))
  }
}

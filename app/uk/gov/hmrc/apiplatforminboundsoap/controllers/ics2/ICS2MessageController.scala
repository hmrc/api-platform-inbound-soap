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

package uk.gov.hmrc.apiplatforminboundsoap.controllers.ics2

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful
import scala.xml.NodeSeq

import cats.data.NonEmptyList

import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{PassThroughModeAction, SoapErrorResponse, SoapMessageValidateAction, VerifyJwtTokenAction}
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.InboundIcs2MessageService

@Singleton()
class ICS2MessageController @Inject() (
    cc: ControllerComponents,
    passThroughModeAction: PassThroughModeAction,
    verifyJwtTokenAction: VerifyJwtTokenAction,
    soapMessageValidateAction: SoapMessageValidateAction,
    incomingMessageService: InboundIcs2MessageService
  )(implicit ec: ExecutionContext
  ) extends BackendController(cc) with SoapErrorResponse {

  def message(): Action[NodeSeq] = (Action andThen passThroughModeAction andThen verifyJwtTokenAction andThen soapMessageValidateAction).async(parse.xml) {
    implicit request =>
      val requestId = request.headers.get("http_x_request_id").getOrElse("unable to obtain http_x_request_id")
      incomingMessageService.processInboundMessage(request.body) flatMap {
        case SendSuccess(status)               =>
          successful(Status(status).as("application/soap+xml"))
        case SendFailExternal(message, status) =>
          successful(returnErrorResponse(NonEmptyList.one(message), requestId, status))
      }
  }
}

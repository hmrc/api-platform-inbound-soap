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
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{PassThroughModeAction, SoapMessageValidateAction, VerifyJwtTokenAction}
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.InboundMessageService

@Singleton()
class ICS2MessageController @Inject() (
    cc: ControllerComponents,
    passThroughModeAction: PassThroughModeAction,
    verifyJwtTokenAction: VerifyJwtTokenAction,
    soapMessageValidateAction: SoapMessageValidateAction,
    incomingMessageService: InboundMessageService
  )(implicit ec: ExecutionContext
  ) extends BackendController(cc) {

  def message(): Action[NodeSeq] = (Action andThen passThroughModeAction andThen verifyJwtTokenAction andThen soapMessageValidateAction).async(parse.xml) {
    implicit request =>
      incomingMessageService.processInboundMessage(request.body) flatMap {
        case SendSuccess(status)      =>
          Future.successful(Status(status).as("application/soap+xml"))
        case SendFailExternal(status) =>
          Future.successful(new Status(status).as("application/soap+xml"))
      }
  }
}

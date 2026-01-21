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

package uk.gov.hmrc.apiplatforminboundsoap.controllers.confirmation

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful
import scala.xml.NodeSeq

import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apiplatforminboundsoap.connectors.ApiPlatformOutboundSoapConnector
import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{AcknowledgementMessageValidateAction, PassThroughModeAction, VerifyJwtTokenAction}
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

@Singleton()
class ConfirmationController @Inject() (
    apiPlatformOutboundSoapConnector: ApiPlatformOutboundSoapConnector,
    cc: ControllerComponents,
    passThroughModeAction: PassThroughModeAction,
    verifyJwtTokenAction: VerifyJwtTokenAction,
    messageValidateAction: AcknowledgementMessageValidateAction
  )(implicit ec: ExecutionContext
  ) extends BackendController(cc) with ApplicationLogger {

  def message(): Action[NodeSeq] = (Action andThen passThroughModeAction andThen verifyJwtTokenAction andThen messageValidateAction).async(parse.xml) { implicit request =>
    apiPlatformOutboundSoapConnector.postMessage(request.body) flatMap {
      case SendSuccess(status)               =>
        successful(Status(status).as("application/soap+xml"))
      case SendFailExternal(message, status) =>
        logger.warn(s"Sending message failed with status code $status: $message")
        successful(new Status(status).as("application/soap+xml"))
    }
  }
}

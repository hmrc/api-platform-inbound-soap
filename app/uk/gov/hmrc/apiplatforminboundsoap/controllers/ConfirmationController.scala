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

import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.apiplatforminboundsoap.connectors.OutboundConnector
import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.VerifyJwtTokenAction
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendSuccess}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

@Singleton()
class ConfirmationController @Inject() (
    outboundConnector: OutboundConnector,
    cc: ControllerComponents,
    verifyJwtTokenAction: VerifyJwtTokenAction
  )(implicit ec: ExecutionContext
  ) extends BackendController(cc) {

  def message(): Action[NodeSeq] = (Action andThen verifyJwtTokenAction).async(parse.xml) { implicit request =>

    outboundConnector.postMessage(request.body) flatMap {
      case SendSuccess      =>
        Future.successful(Ok.as("application/soap+xml"))
      case SendFail(status) =>
        Future.successful(new Status(status).as("application/soap+xml"))
    }
  }
}

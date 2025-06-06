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

package uk.gov.hmrc.apiplatforminboundsoap.controllers.certex

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful
import scala.xml.NodeSeq

import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{PassThroughModeAction, VerifyJwtTokenAction}

@Singleton()
class CertexMessageController @Inject() (
    cc: ControllerComponents,
    passThroughModeAction: PassThroughModeAction,
    verifyJwtTokenAction: VerifyJwtTokenAction
  )(implicit ec: ExecutionContext
  ) extends BackendController(cc) {

  def message(): Action[NodeSeq] = (Action andThen passThroughModeAction andThen verifyJwtTokenAction).async(parse.xml) {
    implicit request => successful(Status(OK).as("application/soap+xml"))
  }
}

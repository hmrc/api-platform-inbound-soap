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

package uk.gov.hmrc.apiplatforminboundsoap.models

sealed trait SendResult
case class SdesResult(uuid: String, forFilename: Option[String])

case object SendSuccess                                  extends SendResult
case class SdesSendSuccess(uuid: String)                 extends SendResult
case class SdesSendSuccessResult(sdesResult: SdesResult) extends SendResult
case class SendFail(status: Int)                         extends SendResult
case class SendNotAttempted(reason: String)              extends SendResult

sealed trait ParseResult

case class InvalidFormatResult(reason: String) extends ParseResult
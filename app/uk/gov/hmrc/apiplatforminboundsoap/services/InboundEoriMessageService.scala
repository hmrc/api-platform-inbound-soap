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

package uk.gov.hmrc.apiplatforminboundsoap.services

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

import play.api.http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.connectors.EoriServiceConnector
import uk.gov.hmrc.apiplatforminboundsoap.models._
import uk.gov.hmrc.apiplatforminboundsoap.util.{ApplicationLogger, RandomUuidGenerator, ZonedDateTimeHelper}

@Singleton
class InboundEoriMessageService @Inject() (
    eoriServiceConnector: EoriServiceConnector,
    uuidGenerator: RandomUuidGenerator,
    dtHelper: ZonedDateTimeHelper,
    config: EoriServiceConnector.Config
  )(implicit ec: ExecutionContext
  ) extends ApplicationLogger with MimeTypes {

  def processInboundMessage(wholeMessage: NodeSeq)(implicit hc: HeaderCarrier): Future[SendResult] = {
    val extraHeaders: Seq[(String, String)] = buildHeadersToAppend()

    forwardMessage(wholeMessage, extraHeaders)
  }

  private def buildHeadersToAppend(): Seq[(String, String)] = {
    val df            = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneId.of("GMT"))
    val date          = dtHelper.now
    val formattedDate = date.format(df)

    List(
      "Accept"           -> MimeTypes.XML,
      "Authorization"    -> s"Bearer ${config.authToken}",
      "Content-Type"     -> "application/xml; charset=UTF-8",
      "date"             -> formattedDate,
      "source"           -> "MDTP",
      "x-correlation-id" -> uuidGenerator.generateRandomUuid,
      "x-files-included" -> "no"
    )
  }

  private def forwardMessage(soapRequest: NodeSeq, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[SendResult] = {
    eoriServiceConnector.postMessage(soapRequest, headers)
  }
}

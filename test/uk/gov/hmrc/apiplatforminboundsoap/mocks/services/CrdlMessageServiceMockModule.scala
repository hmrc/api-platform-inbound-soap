/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatforminboundsoap.mocks.services

import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import play.api.http.Status.OK

import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendNotAttempted, SendResult, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.InboundCrdlMessageService

trait CrdlMessageServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseCrdlMessageServiceMock {
    def theMock: InboundCrdlMessageService

    object ProcessInboundMessage {

      def succeeds(responseBody: String) = {
        when(theMock.processInboundMessage(*)(using *)).thenReturn(successful(SendSuccess(OK, responseBody)))
      }

      def failsInSending(responseBody: String, status: Int) = {
        when(theMock.processInboundMessage(*)(using *)).thenReturn(successful(SendFailExternal(responseBody, status)))
      }

      def abortsBeforeSending(reason: String) = {
        when(theMock.processInboundMessage(*)(using *)).thenReturn(successful(SendNotAttempted(reason)))
      }
    }
  }

  object CrdlMessageServiceMock extends BaseCrdlMessageServiceMock {
    val theMock = mock[InboundCrdlMessageService]
  }
}

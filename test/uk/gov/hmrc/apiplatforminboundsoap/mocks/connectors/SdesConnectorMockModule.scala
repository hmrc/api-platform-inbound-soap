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

package uk.gov.hmrc.apiplatforminboundsoap.mocks.connectors

import scala.concurrent.Future.successful

import org.mockito.Mockito.*
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector
import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.{SdesSendFailExternal, SdesSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.models.{SdesRequest, SendSuccess}

trait SdesConnectorMockModule extends AnyWordSpec with IdiomaticMockito with Matchers {

  protected trait BaseSdesConnectorMock {
    def theMock: SdesConnector

    object PostMessage {

      def succeeds(request: SdesRequest, uuid: String) = {
        when(theMock.postMessage(refEq(request))(using *)).thenReturn(successful(Right(SdesSuccess(uuid))))
      }

      def succeedsWithCaptors(request: SdesRequest, status: Int, body: String) = {
        when(theMock.postMessage(request)(using *)).thenReturn(successful(SendSuccess(status, body)))
      }

      def failsInSending(status: Int, body: String) = {
        when(theMock.postMessage(any[SdesRequest])(using *)).thenReturn(successful(Left(SdesSendFailExternal(body, status))))
      }

      def verifyCalledWithBody(body: SdesRequest) = {
        theMock.postMessage(body)(using *) was called
      }

      def verifyNotCalled() = {
        verifyNoInteractions(theMock)
      }
    }
  }

  object SdesConnectorMock extends BaseSdesConnectorMock {
    val theMock = mock[SdesConnector]
  }
}

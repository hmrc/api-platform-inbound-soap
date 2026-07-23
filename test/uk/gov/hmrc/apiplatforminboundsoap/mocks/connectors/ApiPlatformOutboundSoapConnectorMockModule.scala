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
import scala.xml.NodeSeq

import org.mockito.Mockito.*
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatforminboundsoap.connectors.ApiPlatformOutboundSoapConnector
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendSuccess}

trait ApiPlatformOutboundSoapConnectorMockModule extends AnyWordSpec with IdiomaticMockito with Matchers {

  protected trait BaseApiPlatformOutboundSoapConnectorMock {
    def theMock: ApiPlatformOutboundSoapConnector

    object PostMessage {

      def succeeds(request: NodeSeq, status: Int, body: String) = {
        when(theMock.postMessage(refEq(request))(using *)).thenReturn(successful(SendSuccess(status, body)))
      }

      def succeedsWithCaptors(request: NodeSeq, status: Int, body: String) = {
        when(theMock.postMessage(request)(using *)).thenReturn(successful(SendSuccess(status, body)))
      }

      def failsInSending(request: NodeSeq, status: Int, body: String) = {
        when(theMock.postMessage(refEq(request))(using *)).thenReturn(successful(SendFailExternal(body, status)))
      }

      def verifyCalledWithBody(body: NodeSeq) = {
        theMock.postMessage(body)(using *) was called
      }

      def verifyNotCalled() = {
        verifyNoInteractions(theMock)
      }
    }
  }

  object ApiPlatformOutboundSoapConnectorMock extends BaseApiPlatformOutboundSoapConnectorMock {
    val theMock = mock[ApiPlatformOutboundSoapConnector]
  }
}

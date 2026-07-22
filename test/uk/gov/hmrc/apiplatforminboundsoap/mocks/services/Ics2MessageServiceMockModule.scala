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
import scala.xml.NodeSeq

import org.mockito.ArgumentMatchersSugar
import org.mockito.Mockito.*
import org.mockito.scalatest.IdiomaticMockito

import play.api.http.Status.OK

import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFailExternal, SendNotAttempted, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.InboundIcs2MessageService

trait Ics2MessageServiceMockModule extends IdiomaticMockito with ArgumentMatchersSugar {

  protected trait BaseIcs2MessageServiceMock {
    def theMock: InboundIcs2MessageService

    object ProcessInboundMessage {

      def succeeds(responseBody: String) = {
        when(theMock.processInboundMessage(any[NodeSeq], refEq(false))(using *)).thenReturn(successful(SendSuccess(OK, responseBody)))
      }

      def succeedsForTestMessage(responseBody: String) = {
        when(theMock.processInboundMessage(any[NodeSeq], refEq(true))(using *)).thenReturn(successful(SendSuccess(OK, responseBody)))
      }

      def failsInSending(responseBody: String, status: Int) = {
        when(theMock.processInboundMessage(any[NodeSeq], refEq(false))(using *)).thenReturn(successful(SendFailExternal(responseBody, status)))
      }

      def failsInSendingTestMessage(responseBody: String, status: Int) = {
        when(theMock.processInboundMessage(any[NodeSeq], refEq(true))(using *)).thenReturn(successful(SendFailExternal(responseBody, status)))
      }

      def abortsBeforeSending(reason: String) = {
        when(theMock.processInboundMessage(*)(using *)).thenReturn(successful(SendNotAttempted(reason)))
      }

      def verifyCalledWithBody(body: NodeSeq) = {
        theMock.processInboundMessage(body)(using *) was called
      }

      def verifyCalledWithBodyForTestMessage(body: NodeSeq, isTest: Boolean) = {
        theMock.processInboundMessage(body, isTest)(using *) was called
      }

      def verifyNotCalled() = {
        verifyNoInteractions(theMock)
      }
    }
  }

  object Ics2MessageServiceMock extends BaseIcs2MessageServiceMock {
    val theMock = mock[InboundIcs2MessageService]
  }
}

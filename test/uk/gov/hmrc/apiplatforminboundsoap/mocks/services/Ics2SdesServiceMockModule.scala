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
import scala.language.postfixOps
import scala.xml.NodeSeq

import org.mockito.Mockito.*
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.{SdesSendFailExternal, SdesSendNotAttempted, SdesSuccess, SdesSuccessResult}
import uk.gov.hmrc.apiplatforminboundsoap.models.SdesReference
import uk.gov.hmrc.apiplatforminboundsoap.services.Ics2SdesService

trait Ics2SdesServiceMockModule extends AnyWordSpec with IdiomaticMockito with Matchers {

  protected trait BaseIcs2SdesServiceMock {
    def theMock: Ics2SdesService

    object ProcessMessage {

      def succeeds(request: NodeSeq, sdesReference: (String, String)*)    = {
        when(theMock.processMessage(refEq(request))(using *)).thenReturn(successful(sdesReference.map((f, u) => Right(SdesSuccessResult(SdesReference(f, u)))).toList))
      }

      def failsInSending(message: String, status: Int)                    = {
        when(theMock.processMessage(*)(using *)).thenReturn(successful(List(Left(SdesSendFailExternal(message, status)))))
      }

      def notAllRequestsSucceed(failedMessage: String, failedStatus: Int) = {
        when(theMock.processMessage(*)(using *)).thenReturn(successful(List(Right(SdesSuccess("some-uuid")), Left(SdesSendFailExternal(failedMessage, failedStatus)))))
      }

      def abortsBeforeSending(message: String)                            = {
        when(theMock.processMessage(*)(using *)).thenReturn(successful(List(Left(SdesSendNotAttempted(message)))))
      }

      def verifyCalledWithBody(body: NodeSeq)                             = {
        theMock.processMessage(body)(using *) was called
      }

      def verifyNotCalled()                                               = {
        theMock.processMessage(*)(using *) wasNever called
      }

      def verifyNotCalledAgain()                                          = {
        verifyNoMoreInteractions(theMock)
      }
    }
  }

  object Ics2SdesServiceMock extends BaseIcs2SdesServiceMock {
    val theMock = mock[Ics2SdesService]
  }
}

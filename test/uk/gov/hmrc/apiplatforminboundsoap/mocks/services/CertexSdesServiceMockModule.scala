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

import org.mockito.Mockito.*
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.{SdesSendFailExternal, SdesSendNotAttempted, SdesSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.CertexSdesService

trait CertexSdesServiceMockModule extends AnyWordSpec with IdiomaticMockito with Matchers {

  protected trait BaseCertexSdesServiceMock {
    def theMock: CertexSdesService

    object ProcessMessage {

      def succeeds(request: NodeSeq, sdesUuid: String) = {
        when(theMock.processMessage(refEq(request))(using *)).thenReturn(successful(List(Right(SdesSuccess(s"$sdesUuid")))))
      }

      def failsInSending(message: String, status: Int) = {
        when(theMock.processMessage(*)(using *)).thenReturn(successful(List(Left(SdesSendFailExternal(message, status)))))
      }

      def abortsBeforeSending(message: String) = {
        when(theMock.processMessage(*)(using *)).thenReturn(successful(List(Left(SdesSendNotAttempted(message)))))
      }

      def verifyCalledWithBody(body: NodeSeq) = {
        theMock.processMessage(body)(using *) was called
      }
    }
  }

  object CertexSdesServiceMock extends BaseCertexSdesServiceMock {
    val theMock = mock[CertexSdesService]
  }
}

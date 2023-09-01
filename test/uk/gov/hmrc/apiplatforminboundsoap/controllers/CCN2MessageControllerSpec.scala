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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.xml.{Elem, XML}

import org.mockito.captor.{ArgCaptor, Captor}
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatforminboundsoap.models.{SendFail, SendSuccess}
import uk.gov.hmrc.apiplatforminboundsoap.services.InboundMessageService
import uk.gov.hmrc.apiplatformoutboundsoap.controllers.actionBuilders.VerifyJwtTokenAction

class CCN2MessageControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    private val verifyJwtTokenAction = app.injector.instanceOf[VerifyJwtTokenAction]
    val incomingMessageServiceMock   = mock[InboundMessageService]
    val controller                   = new CCN2MessageController(Helpers.stubControllerComponents(), verifyJwtTokenAction, incomingMessageServiceMock)
  }

  "POST CCN2 message endpoint " should {
    val fakeRequest = FakeRequest("POST", "/ics2/NESControlBASV2")
      .withHeaders("Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwMDc5NzcwNzd9.bgdyMvTvicf5FvAlQXN-311k0WTZg0-72wqR4hb66dQ")
      .withHeaders("Content-Type" -> "text/xml")

    "return 200 when successful" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val requestBody: Elem              = XML.loadString("<xml>blah</xml>")
      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor)(*)).thenReturn(successful(SendSuccess))

      val result = controller.message("NESControlBASV2")(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
      verify(incomingMessageServiceMock).processInboundMessage(*)(*)
      xmlRequestCaptor hasCaptured requestBody
    }

    "return response code it received when not successful" in new Setup {
      val xmlRequestCaptor: Captor[Elem] = ArgCaptor[Elem]
      val requestBody: Elem              = XML.loadString("<xml>blah</xml>")
      when(incomingMessageServiceMock.processInboundMessage(xmlRequestCaptor)(*)).thenReturn(successful(SendFail(PRECONDITION_FAILED)))

      val result = controller.message("NESControlBASV2")(fakeRequest.withBody(requestBody))

      status(result) shouldBe PRECONDITION_FAILED
      verify(incomingMessageServiceMock).processInboundMessage(*)(*)
      xmlRequestCaptor hasCaptured requestBody
    }
  }

}

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

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.Elem

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.{PassThroughModeAction, VerifyJwtTokenAction}

class CrdlMessageControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {

    val app: Application      = new GuiceApplicationBuilder()
      .configure("passThroughEnabled" -> "false")
      .build()
    val xRequestIdHeaderValue = randomUUID.toString()

    val commonHeaders = Headers(
      "Host"         -> "localhost",
      "x-request-id" -> xRequestIdHeaderValue,
      "Content-Type" -> "text/xml"
    )

    val headersWithValidBearerToken = commonHeaders.add(
      "Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwNDM1NzAwNDUsImlzcyI6ImMzYTlhMTAxLTkzN2ItNDdjMS1iYzM1LWJkYjI0YjEyZTRlNSJ9.00ASmOrt3Ze6DNNGYhWLXWRWWO2gvPjC15G2K5D8fXU"
    )

    private val passThroughModeAction = app.injector.instanceOf[PassThroughModeAction]
    private val verifyJwtTokenAction  = app.injector.instanceOf[VerifyJwtTokenAction]

    val controller  =
      new CrdlMessageController(Helpers.stubControllerComponents(), passThroughModeAction, verifyJwtTokenAction)
    val fakeRequest = FakeRequest("POST", "/crdl/incoming").withHeaders(headersWithValidBearerToken)
  }

  "POST CRDL message endpoint" should {
    "return 200" in new Setup {
      val requestBody: Elem = <xml>foobar</xml>

      val result = controller.message()(fakeRequest.withBody(requestBody))

      status(result) shouldBe OK
    }
  }
}

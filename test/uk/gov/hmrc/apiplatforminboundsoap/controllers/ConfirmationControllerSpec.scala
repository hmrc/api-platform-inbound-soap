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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}

import uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders.VerifyJwtTokenAction

class ConfirmationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  trait Setup {
    private val verifyJwtTokenAction = app.injector.instanceOf[VerifyJwtTokenAction]
    val controller                   = new ConfirmationController(Helpers.stubControllerComponents(), verifyJwtTokenAction)
  }

  "POST acknowledgement endpoint with no authorisation header" should {
    val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
    "return 403" in new Setup {
      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.UNAUTHORIZED
    }
  }

  "POST acknowledgement endpoint with empty authorisation header" should {
    val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
      .withHeaders("Authorization" -> "Bearer")
    "return 403" in new Setup {
      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.UNAUTHORIZED
    }
  }

  "POST acknowledgement endpoint with exp claim in the past" should {
    val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
      .withHeaders("Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2OTIzNTkxNDJ9.SLEu_OlkHea19WwAYq_wG5nRJ43-uBv013QH3U_Gqvs")
    "return 403" in new Setup {
      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.UNAUTHORIZED
    }
  }

  "POST acknowledgement endpoint with valid authorisation header" should {
    val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
      .withHeaders("Authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjIwMDc5NzcwNzd9.bgdyMvTvicf5FvAlQXN-311k0WTZg0-72wqR4hb66dQ")
    "return 200" in new Setup {
      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.OK
    }
  }
}

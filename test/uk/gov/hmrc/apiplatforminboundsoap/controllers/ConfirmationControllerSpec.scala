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
import uk.gov.hmrc.apiplatformoutboundsoap.controllers.actionBuilders.VerifyJwtTokenAction

class ConfirmationControllerSpec extends AnyWordSpec with Matchers  with GuiceOneAppPerSuite {

  trait Setup {
    private val verifyJwtTokenAction = app.injector.instanceOf[VerifyJwtTokenAction]
    val controller = new ConfirmationController(Helpers.stubControllerComponents(),
       verifyJwtTokenAction)
  }
  private val fakeRequest = FakeRequest("POST", "/ccn2/acknowledgementV2")
  "POST acknowledgement endpoint with no authorisation header" should {
    "return 403" in new Setup {
      val result = controller.message()(fakeRequest)
      status(result) shouldBe Status.FORBIDDEN
    }
  }
}

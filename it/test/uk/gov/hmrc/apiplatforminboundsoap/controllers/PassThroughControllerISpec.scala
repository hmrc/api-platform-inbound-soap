/*
 * Copyright 2024 HM Revenue & Customs
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

import scala.io.Source
import scala.xml.{Elem, XML}

import com.github.tomakehurst.wiremock.http.Fault
import org.apache.pekko.stream.Materializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.http.test.WireMockSupport

import uk.gov.hmrc.apiplatforminboundsoap.support.ExternalServiceStub

class PassThroughControllerISpec extends AnyWordSpecLike with Matchers with WireMockSupport with GuiceOneAppPerSuite with ExternalServiceStub {
  override implicit lazy val app: Application = appBuilder.build()
  implicit val mat: Materializer              = app.injector.instanceOf[Materializer]

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"    -> false,
        "auditing.enabled"   -> false,
        "forwardMessageHost" -> wireMockHost,
        "forwardMessagePort" -> wireMockPort
      )

  val path        = "/ics2/NESReferralBASV2"
  val fakeRequest = FakeRequest("POST", path)

  val underTest: PassThroughController = app.injector.instanceOf[PassThroughController]
  "message" should {
    "forward an XML message" in {
      val expectedStatus = 202
      primeStubForSuccess("OK", expectedStatus, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

      val result = underTest.message(path)(fakeRequest.withXmlBody(payload))
      status(result) shouldBe expectedStatus
    }

    "forward to a valid URL when path is missing a leading stroke (as the Router does it)" in {
      val pathWithoutLeadingStroke = "ics2/NESReferralBASV2"
      val expectedStatus           = 202
      primeStubForSuccess("OK", expectedStatus, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

      val result = underTest.message(pathWithoutLeadingStroke)(fakeRequest.withXmlBody(payload))
      status(result) shouldBe expectedStatus
    }

    "forward an XML message to the right path" in {
      val paths = Table(
        "path",
        "/ics2/NESErrorNotificationBAS",
        "/ics2/NESNotificationBAS",
        "/ics2/NESReferralBAS",
        "/ics2/NESRiskAnalyisBAS",
        "/ics2/NESRiskAnalysisBAS",
        "/ics2/NESControlBASV2",
        "/ics2/NESErrorNotificationBASV2",
        "/ics2/NESNotificationBASV2",
        "/ics2/NESReferralBASV2",
        "/ics2/NESRiskAnalyisBASV2",
        "/ics2/NESRiskAnalysisBASV2"
      )

      val expectedStatus = 202

      forAll(paths) { path: String =>
        val fakeRequest = FakeRequest("POST", "/")
        primeStubForSuccess("OK", expectedStatus, path)

        val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

        val result = underTest.message(path)(fakeRequest.withXmlBody(payload))
        status(result) shouldBe expectedStatus
      }
    }

    "reject an XML message with the wrong Content-Type header" in {
      val expectedStatus = 400
      primeStubForSuccess("OK", expectedStatus, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())

      val result = underTest.message(path)(fakeRequest.withXmlBody(payload).withHeaders("Content-Type" -> "application/json"))
      status(result) shouldBe expectedStatus
    }

    "reject a JSON message with application/soap+xml Content-Type header" in {
      val expectedStatus = 400
      primeStubForSuccess("OK", expectedStatus, path)

      val result = underTest.message(path)(fakeRequest.withJsonBody(Json.toJson(Json.obj("foo" -> "bar")))
        .withHeaders("Content-Type" -> "application/soap+xml"))
      status(result) shouldBe expectedStatus
    }
    "handle an error response from the forward-to service " in {
      val expectedStatus = 500
      primeStubForFault("Something went wrong", Fault.CONNECTION_RESET_BY_PEER, path)

      val payload: Elem = XML.load(Source.fromResource("ie4r02-v2.xml").bufferedReader())
      val result        = underTest.message(path)(fakeRequest.withXmlBody(payload))

      status(result) shouldBe expectedStatus
    }
  }
}

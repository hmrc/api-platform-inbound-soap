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

package uk.gov.hmrc.apiplatforminboundsoap.stubs

import scala.xml.Elem

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.StubMapping

trait ApiPlatformOutboundSoapStub {

  def primeStubForSuccess(body: Elem, responseStatus: Int, path: String = "/") = {
    stubFor(
      post(urlEqualTo(path))
        .withRequestBody(equalToXml(body.mkString))
        .willReturn(
          aResponse()
            .withStatus(responseStatus)
        )
    )
  }

  def primeStubForFault(requestBody: Elem, responseBody: String, fault: Fault, path: String = "/"): StubMapping = {
    stubFor(post(urlPathEqualTo(path))
      .withRequestBody(equalToXml(requestBody.mkString))
      .willReturn(
        aResponse()
          .withBody(responseBody)
          .withFault(fault)
      ))
  }

  def verifyRequestBody(body: Elem, path: String = "/"): Unit = {
    verify(postRequestedFor(urlPathEqualTo(path))
      .withRequestBody(equalToXml(body.mkString)))
  }

  def verifyHeader(headerName: String, headerValue: String, path: String = "/"): Unit = {
    verify(postRequestedFor(urlPathEqualTo(path)).withHeader(headerName, equalTo(headerValue)))
  }

}

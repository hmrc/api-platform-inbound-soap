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

package uk.gov.hmrc.apiplatforminboundsoap.xml

class SoapErrorResponse  {
  val body: String =
  """<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
    |    <soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
    |    <soap:Body>
    |        <soap:Fault>
    |            <soap:Code>
    |                <soap:Value>soap:$statusCode</soap:Value>
    |            </soap:Code>
    |            <soap:Reason>
    |                <soap:Text xml:lang="en">$reason</soap:Text>
    |            </soap:Reason>
    |            <soap:Node>public-soap-proxy</soap:Node>
    |            <soap:Detail>
    |                <RequestId>$requestId</RequestId>
    |            </soap:Detail>
    |        </soap:Fault>
    |    </soap:Body>
    |</soap:Envelope>""".stripMargin


}

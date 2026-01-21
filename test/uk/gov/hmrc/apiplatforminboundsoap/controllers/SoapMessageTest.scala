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

package uk.gov.hmrc.apiplatforminboundsoap.controllers

import java.util.UUID.randomUUID
import scala.io.Source
import scala.xml.XML

import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.DiffBuilder.compare
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors.byName

trait SoapMessageTest {
  val xRequestIdHeaderValue = randomUUID.toString

  def readFromFile(fileName: String) = {
    XML.load(Source.fromResource(fileName).bufferedReader())
  }

  def expectedSoapResponse(reason: String, status: Int = 400) =
    s"""<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
       |<soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
       |<soap:Body>
       |<soap:Fault>
       |<soap:Code>
       |<soap:Value>soap:$status</soap:Value>
       |</soap:Code>
       |<soap:Reason>
       |<soap:Text xml:lang="en">$reason</soap:Text>
       |</soap:Reason>
       |<soap:Node>api-platform-inbound-soap</soap:Node>
       |<soap:Detail>
       |<RequestId>$xRequestIdHeaderValue</RequestId>
       |</soap:Detail>
       |</soap:Fault>
       |</soap:Body>
       |</soap:Envelope>
       |""".stripMargin

  def getXmlDiff(actual: String, expected: String): DiffBuilder = {
    compare(expected)
      .withTest(actual)
      .withNodeMatcher(new DefaultNodeMatcher(byName))
      .checkForIdentical
      .ignoreComments
      .ignoreWhitespace
  }
}

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

import scala.io.Source
import scala.xml.{Elem, NodeSeq}

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.xmlunit.builder.DiffBuilder.compare
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors.byName

class CrdlXmlHelperSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar with CrdlXmlHelper {

  private def getXmlDiff(actual: NodeSeq, expected: Elem): DiffBuilder = {
    compare(Input.fromString(expected.toString).build())
      .withTest(Input.fromString(actual.toString()).build())
      .withNodeMatcher(new DefaultNodeMatcher(byName))
      .checkForIdentical()
  }

  "taskIdentifier" should {
    "return task identifier from SOAP message" in new Setup {
      val xmlBody: Elem       = readFromFile("crdl/crdl-request-well-formed.xml")
      val foundTaskIdentifier = taskIdentifier(xmlBody)
      foundTaskIdentifier shouldBe Some("13933062")
    }

    "return None when task identifier not found in SOAP message" in new Setup {
      val foundTaskIdentifier = taskIdentifier(xmlBodyForElementNotFoundScenario)
      foundTaskIdentifier shouldBe None
    }
  }

  "replaceAttachment" should {
    "replace contents of ReceiveReferenceDataRequestResult element with provided replacement" in new Setup {
      val xmlRequestBody: Elem             = readFromFile("crdl/crdl-request-well-formed.xml")
      val xmlRequestAfterReplacement: Elem = readFromFile("post-sdes-processing/crdl/crdl-request-well-formed.xml")
      val afterTransformation              = replaceAttachment(xmlRequestBody, "some-uuid-like-string")
      getXmlDiff(afterTransformation, xmlRequestAfterReplacement).build().hasDifferences shouldBe false
    }
  }

  trait Setup {
    val xmlBodyForElementNotFoundScenario: NodeSeq = <xml>blah</xml>

    def readFromFile(fileName: String) = {
      xml.XML.load(Source.fromResource(fileName).bufferedReader())
    }
  }
}

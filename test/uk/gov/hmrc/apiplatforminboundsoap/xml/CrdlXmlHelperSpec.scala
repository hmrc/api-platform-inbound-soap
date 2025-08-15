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

class CrdlXmlHelperSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar with CrdlXml {

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
      val afterTransformation              = xmlTransformer.replaceAttachment(xmlRequestBody, "some-uuid-like-string")
      getXmlDiff(afterTransformation, xmlRequestAfterReplacement).build().hasDifferences shouldBe false
    }
  }
  "getBinaryAttachment" should {
    "get embedded attachment when one exists" in new Setup {
      val xmlRequestBody: Elem = readFromFile("crdl/crdl-request-well-formed.xml")
      getBinaryAttachment(xmlRequestBody).text shouldBe "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPG5zMTpJbXBvcnRSZWZlcmVuY2VEYXRhRW50cnlSZXNwTXNnIHhzaTpzY2hlbWFMb2NhdGlvbj0iIiB4bWxucz0iaHR0cDovL3htbG5zLmVjLmV1L0J1c2luZXNzT2JqZWN0cy9DU1JEMi9SZWZlcmVuY2VEYXRhRW50cnlNYW5hZ2VtZW50QkFTU2VydmljZVR5cGUvVjQiIHhtbG5zOm5zMD0iaHR0cDovL3htbG5zLmVjLmV1L0J1c2luZXNzT2JqZWN0cy9DU1JEMi9Db21tb25TZXJ2aWNlVHlwZS9WMyIgeG1sbnM6bnMyPSJodHRwOi8veG1sbnMuZWMuZXUvQnVzaW5lc3NPYmplY3RzL0NTUkQyL01lc3NhZ2VIZWFkZXJUeXBlL1YyIiB4bWxuczpuczE9Imh0dHA6Ly94bWxucy5lYy5ldS9CdXNpbmVzc0FjdGl2aXR5U2VydmljZS9DU1JEMi9JUmVmZXJlbmNlRGF0YUVudHJ5TWFuYWdlbWVudEJBUy9WNCIgeG1sbnM6eHNpPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYS1pbnN0YW5jZSI+CiAgIDxuczA6TWVzc2FnZUhlYWRlci8+CiAgIDxuczA6YWNrbm93bGVkZ2VtZW50Pk9LPC9uczA6YWNrbm93bGVkZ2VtZW50Pgo8L25zMTpJbXBvcnRSZWZlcmVuY2VEYXRhRW50cnlSZXNwTXNnPgo="
    }

    "return empty string for embedded attachment when element is empty" in new Setup {
      val xmlRequestBody: Elem = readFromFile("crdl/crdl-request-empty-attachment-element.xml")
      getBinaryAttachment(xmlRequestBody).text shouldBe ""
    }

    "return None for embedded attachment when element is absent" in new Setup {
      val xmlRequestBody: Elem = readFromFile("crdl/crdl-request-no-attachment.xml")
      getBinaryAttachment(xmlRequestBody) shouldBe NodeSeq.Empty
    }
  }

  trait Setup {
    val xmlBodyForElementNotFoundScenario: NodeSeq = <xml>blah</xml>

    def readFromFile(fileName: String) = {
      xml.XML.load(Source.fromResource(fileName).bufferedReader())
    }
  }
}

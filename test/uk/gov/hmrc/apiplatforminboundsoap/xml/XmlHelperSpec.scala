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

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import scala.io.Source
import scala.xml.{Elem, NodeSeq}

class XmlHelperSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val xmlHelper: XmlHelper = new XmlHelper()
    val xmlBodyForElementNotFoundScenario: NodeSeq = xml.XML.loadString("<xml>blah</xml>")

    def readFromFile(fileName: String) = {
      xml.XML.load(Source.fromResource(fileName).bufferedReader())
    }
  }

  "getSoapAction" should {
    "return SOAP action from SOAP message" in new Setup {
      val xmlBody: Elem = readFromFile("ie4n09-v2.xml")
      val soapAction = xmlHelper.getSoapAction(xmlBody)
      soapAction shouldBe "CCN2.Service.Customs.EU.ICS.ENSLifecycleManagementBAS/IE4N09notifyControlDecision"
    }

    "return empty string when SOAP action not found in SOAP message" in new Setup {
      val soapAction = xmlHelper.getSoapAction(xmlBodyForElementNotFoundScenario)
      soapAction shouldBe "Not defined"
    }
  }

  "get messageId" should {
    "return messageId from SOAP message" in new Setup {
      val xmlBody: Elem = readFromFile("ie4n09-v2.xml")
      val messageId = xmlHelper.getMessageId(xmlBody)
      messageId shouldBe "dc4f3c40-5fb9-44eb-a327-d431611a9521"
    }

    "return empty string when SOAP action not found in SOAP message" in new Setup {
      val messageId = xmlHelper.getMessageId(xmlBodyForElementNotFoundScenario)
      messageId shouldBe "Not defined"
    }
  }

  "get version namespace" should {
    "return whether V1 namespace found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4n05-v1.xml")
      xmlHelper.getMessageVersion(xmlBody).displayName shouldBe "V1"
    }
    "return whether V2 namespace found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4n09-v2.xml")
      xmlHelper.getMessageVersion(xmlBody).displayName shouldBe "V2"
    }

    "return whether invalid namespace found in SOAP message" in new Setup {
      xmlHelper.getMessageVersion(xmlBodyForElementNotFoundScenario).displayName shouldBe "Not Recognised"
    }
  }

  "isFileAttached" should {
    "return true when binaryFile found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4s03-v2.xml")
      xmlHelper.isFileAttached(xmlBody) shouldBe true
    }

    "return true when binaryAttachment found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2.xml")
      xmlHelper.isFileAttached(xmlBody) shouldBe true
    }

    "return false when no binaryAttachment or binaryFile found in SOAP message" in new Setup {
      xmlHelper.isFileAttached(xmlBodyForElementNotFoundScenario) shouldBe false
    }
  }

  "getReferenceNumber" should {
    "return MRN when one is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4s03-v2.xml")
      xmlHelper.getReferenceNumber(xmlBody) shouldBe "7c1aa850-9760-42ab-bebe-709e3a4a888f"
    }

    "return LRN when one is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4s03-with-LRN-v2.xml")
      xmlHelper.getReferenceNumber(xmlBody) shouldBe "836478b5-9290-47fa-a549-9d7ca1d1d77d"
    }

    "return empty string when no LRN or MRN is found in SOAP message" in new Setup {
      xmlHelper.getReferenceNumber(xmlBodyForElementNotFoundScenario) shouldBe ""
    }
  }
}

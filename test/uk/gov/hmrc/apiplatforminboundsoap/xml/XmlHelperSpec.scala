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

class XmlHelperSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val xmlHelper: XmlHelper                       = new XmlHelper()
    val xmlBodyForElementNotFoundScenario: NodeSeq = xml.XML.loadString("<xml>blah</xml>")

    def readFromFile(fileName: String) = {
      xml.XML.load(Source.fromResource(fileName).bufferedReader())
    }
  }

  "getSoapAction" should {
    "return SOAP action text from SOAP message" in new Setup {
      val xmlBody: Elem = readFromFile("ie4n09-v2.xml")
      val soapAction    = xmlHelper.getSoapAction(xmlBody)
      soapAction shouldBe Some("CCN2.Service.Customs.EU.ICS.ENSLifecycleManagementBAS/IE4N09notifyControlDecision")
    }

    "return None when SOAP action not found in SOAP message" in new Setup {
      val soapAction = xmlHelper.getSoapAction(xmlBodyForElementNotFoundScenario)
      soapAction shouldBe None
    }
  }

  "get messageId" should {
    "return messageId from SOAP message" in new Setup {
      val xmlBody: Elem = readFromFile("ie4n09-v2.xml")
      val Some(messageId)     = xmlHelper.getMessageId(xmlBody)
      messageId shouldBe "dc4f3c40-5fb9-44eb-a327-d431611a9521"
    }

    "return empty string when SOAP action not found in SOAP message" in new Setup {
      val messageId = xmlHelper.getMessageId(xmlBodyForElementNotFoundScenario)
      messageId shouldBe None
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
      xmlHelper.getReferenceNumber(xmlBody) shouldBe Some("7c1aa850-9760-42ab-bebe-709e3a4a888f")
    }

    "return LRN when one is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4s03-with-LRN-v2.xml")
      xmlHelper.getReferenceNumber(xmlBody) shouldBe Some("836478b5-9290-47fa-a549-9d7ca1d1d77d")
    }

    "return empty string when no LRN or MRN is found in SOAP message" in new Setup {
      xmlHelper.getReferenceNumber(xmlBodyForElementNotFoundScenario) shouldBe None
    }
  }

  "getFilename" should {
    "return filename when one is found in SOAP message within binaryAttachment or binaryFile" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4s03-v2.xml")
      xmlHelper.getBinaryFilename(xmlBody) shouldBe "test-filename.txt"
    }

    "return empty string when no filename is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("filename/ie4r02-v2-blank-filename-element.xml")
      xmlHelper.getBinaryFilename(xmlBody) shouldBe ""
    }
  }

  "getMimeType" should {
    "return MIME when one is found in SOAP message within binaryAttachment or binaryFile" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4s03-v2.xml")
      xmlHelper.getBinaryMimeType(xmlBody) shouldBe "application/pdf"
    }

    "return empty string when no filename is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("MIME/ie4r02-v2-missing-mime-element.xml")
      xmlHelper.getBinaryMimeType(xmlBody) shouldBe ""
    }
  }

  "getDescription" should {
    "return description when one is found in SOAP message within binaryAttachment or binaryFile" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4s03-v2.xml")
      xmlHelper.getBinaryDescription(xmlBody) shouldBe "a file made up for unit testing"
    }

    "return empty string when no description is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("description/ie4r02-v2-blank-description-element.xml")
      xmlHelper.getBinaryDescription(xmlBody) shouldBe ""
    }
  }

  "getReferralRequestReference" should {
    "return referralRequestReference when one is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2.xml")
      xmlHelper.getReferralRequestReference(xmlBody) shouldBe "d4af29b4-d1d7-4f42-a186-ca5a71fab"
    }

    "return empty string when no referralRequestReference is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2-blank-referralRequestReference-element.xml")
      xmlHelper.getReferralRequestReference(xmlBody) shouldBe ""
    }
  }

  "getBinaryObject" should {
    "return binaryObject element value when one is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2.xml")
      xmlHelper.getBinaryBase64Object(xmlBody) shouldBe "dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo="
    }

    "return empty string when no binaryObject is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2-blank-includedBinaryObject-element.xml")
      xmlHelper.getBinaryBase64Object(xmlBody) shouldBe ""
    }

    "getBinaryUri" should {
    "return binaryObject URI element value when one is found in SOAP message within binaryAttachment element" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2-binaryAttachment-with-uri.xml")
      xmlHelper.getBinaryUri(xmlBody) shouldBe Some("https://dummyhost.ec.eu")
    }

    "return empty string when no binaryAttachment URI is found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2-missing-attachment-uri-element.xml")
      xmlHelper.getBinaryUri(xmlBody) shouldBe None
    }
  }
}
}

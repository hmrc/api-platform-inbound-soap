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

    def readFromString(xmlString: String) = {
      xml.XML.loadString(xmlString)
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
      val xmlBody: Elem   = readFromFile("ie4n09-v2.xml")
      val Some(messageId) = xmlHelper.getMessageId(xmlBody)
      messageId shouldBe "ad7f2ad2d4f5-4606-99a0-0dd4e52be116"
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
      val binaryFile = """<urn:binaryFile>
                         |                            <urn:filename>test-filename.txt</urn:filename>
                         |                            <urn:URI>?</urn:URI>
                         |                            <urn:MIME>application/pdf</urn:MIME>
                         |                            <urn:includedBinaryObject>cid:1177341525550</urn:includedBinaryObject>
                         |                            <urn:description>a file made up for unit testing</urn:description>
                         |                        </urn:binaryFile>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryFile)
      xmlHelper.getBinaryFilename(xmlBody) shouldBe "test-filename.txt"
    }

    "return empty string when no filename is found in SOAP message" in new Setup {
      val binaryAttachment = """<urn:binaryAttachment>
                               |                  <urn:filename></urn:filename>
                               |                  <urn:MIME>?</urn:MIME>
                               |                  <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
                               |                  <urn:description>?</urn:description>
                               |               </urn:binaryAttachment>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryAttachment)
      xmlHelper.getBinaryFilename(xmlBody) shouldBe ""
    }
  }

  "getMimeType" should {
    "return MIME when one is found in SOAP message within binaryAttachment or binaryFile" in new Setup {
      val binaryFile = """<urn:binaryFile>
                         |                            <urn:filename>test-filename.txt</urn:filename>
                         |                            <urn:URI>?</urn:URI>
                         |                            <urn:MIME>application/pdf</urn:MIME>
                         |                            <urn:includedBinaryObject>cid:1177341525550</urn:includedBinaryObject>
                         |                            <urn:description>a file made up for unit testing</urn:description>
                         |                        </urn:binaryFile>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryFile)
      xmlHelper.getBinaryMimeType(xmlBody) shouldBe "application/pdf"
    }

    "return empty string when no filename is found in SOAP message" in new Setup {
      val binaryAttachment = """<urn:binaryAttachment>
                               |                  <urn:filename>?</urn:filename>
                               |                  <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
                               |                  <urn:description>?</urn:description>
                               |               </urn:binaryAttachment>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryAttachment)
      xmlHelper.getBinaryMimeType(xmlBody) shouldBe ""
    }
  }

  "getDescription" should {
    "return description when one is found in SOAP message within binaryAttachment or binaryFile" in new Setup {
      val binaryFile = """<urn:binaryFile>
                         |                            <urn:filename>test-filename.txt</urn:filename>
                         |                            <urn:URI>?</urn:URI>
                         |                            <urn:MIME>application/pdf</urn:MIME>
                         |                            <urn:includedBinaryObject>cid:1177341525550</urn:includedBinaryObject>
                         |                            <urn:description>a file made up for unit testing</urn:description>
                         |                        </urn:binaryFile>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryFile)
      xmlHelper.getBinaryDescription(xmlBody) shouldBe "a file made up for unit testing"
    }

    "return empty string when no description is found in SOAP message" in new Setup {
       val binaryAttachment = """<urn:binaryAttachment>
                                |                  <urn:filename>?</urn:filename>
                                |                  <urn:MIME>?</urn:MIME>
                                |                  <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
                                |                  <urn:description></urn:description>
                                |               </urn:binaryAttachment>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryAttachment)
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

  "getBinaryElement" should {
    "return 2 binaryElements when 2 are found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2-both-binaryFile-and-binaryAttachment-elements-files-inline.xml")
      xmlHelper.getBinaryElements(xmlBody).size shouldBe 2
    }

    "return 3 binaryElements when 3 are found in SOAP message" in new Setup {
      val xmlBody: NodeSeq = readFromFile("ie4r02-v2-one-binaryFile-and-two-binaryAttachment-elements-files-inline.xml")
      xmlHelper.getBinaryElements(xmlBody).size shouldBe 3
    }
  }

  "getBinaryObject" should {
    "return binaryObject element value from binaryAttachment element" in new Setup {
      val binaryAttachment = """<urn:binaryAttachment>
                               |                  <urn:filename>?</urn:filename>
                               |                  <urn:MIME>?</urn:MIME>
                               |                  <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
                               |                  <urn:description>?</urn:description>
                               |               </urn:binaryAttachment>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryAttachment)
      xmlHelper.getBinaryBase64Object(xmlBody) shouldBe Some("dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=")
    }

   "return binaryObject element value from binaryFile element" in new Setup {
     val binaryFile = """<urn:binaryFile>
               |                  <urn:filename>filename2.txt</urn:filename>
               |                  <urn:MIME>text/plain</urn:MIME>
               |                  <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
               |                  <urn:description>A texty sort of file</urn:description>
               |               </urn:binaryFile>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryFile)
      xmlHelper.getBinaryBase64Object(xmlBody) shouldBe Some("dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=")
    }

    "return empty string when no binaryObject is found in SOAP message" in new Setup {
      val binaryAttachment = """<urn:binaryAttachment>
                               |                  <urn:filename>?</urn:filename>
                               |                  <urn:MIME>?</urn:MIME>
                               |                  <urn:includedBinaryObject></urn:includedBinaryObject>
                               |                  <urn:description>?</urn:description>
                               |               </urn:binaryAttachment>""".stripMargin
      val xmlBody: NodeSeq = readFromString(binaryAttachment)
      xmlHelper.getBinaryBase64Object(xmlBody) shouldBe Some("")
    }

    "getBinaryUri" should {
      "return binaryObject URI element value when one is found in SOAP message within binaryAttachment element" in new Setup {
        val binaryAttachment = """<urn:binaryAttachment>
                                 |                  <urn:filename>?</urn:filename>
                                 |                  <urn:URI>https://dummyhost.ec.eu</urn:URI>
                                 |                  <urn:MIME>?</urn:MIME>
                                 |                  <urn:description>?</urn:description>
                                 |               </urn:binaryAttachment>""".stripMargin
        val xmlBody: NodeSeq = readFromString(binaryAttachment)
        xmlHelper.getBinaryUri(xmlBody) shouldBe Some("https://dummyhost.ec.eu")
      }

      "return empty string when no binaryAttachment URI element is found in SOAP message" in new Setup {
        val binaryAttachment = """<urn:binaryAttachment>
                                 |                  <urn:filename>?</urn:filename>
                                 |                  <urn:MIME>?</urn:MIME>
                                 |                  <urn:includedBinaryObject></urn:includedBinaryObject>
                                 |                  <urn:description>?</urn:description>
                                 |               </urn:binaryAttachment>""".stripMargin
        val xmlBody: NodeSeq = readFromString(binaryAttachment)
        xmlHelper.getBinaryUri(xmlBody) shouldBe None
      }
    }
  }
}

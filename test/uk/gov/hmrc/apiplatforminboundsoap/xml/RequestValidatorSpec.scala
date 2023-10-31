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

import cats.data.NonEmptyList
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class RequestValidatorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar with RequestValidator {

  trait Setup {

    def readFromFile(fileName: String) = {
      xml.XML.load(Source.fromResource(fileName).bufferedReader())
    }

    def readFromString(xmlString: String) = {
      xml.XML.loadString(xmlString)
    }
  }

  "verifyElements" should {
    "return Right(Unit) when all elements are valid" in new Setup {
      val validationResult = verifyElements(readFromFile("ie4n09-v2.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has included binaryFile and included binaryAttachment" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-both-binaryFile-and-binaryAttachment-elements-files-inline.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has included binaryFile and uri binaryAttachment" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-both-binaryAttachment-with-uri-and-binaryFile-with-included-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has uri binaryFile and included binaryAttachment" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-both-binaryAttachment-with-included-and-binaryFile-with-uri-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has uri binaryFile and uri binaryAttachment" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-both-binaryAttachment-with-uri-and-binaryFile-with-uri-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has two binaryFiles with included elements" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryFiles-with-included-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has included binaryFile and uri binaryFile" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-both-binaryFile-with-included-and-binaryFile-with-uri-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has two binaryFiles with uri elements" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryFiles-with-uri-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has two binaryAttachments with included elements" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryAttachments-with-included-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has binaryAttachment with included and binaryAttachment with uri elements" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-binaryAttachment-with-included-and-binaryAttachment-with-uri-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return Right(Unit) when message has two binaryAttachments with uri elements" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-two-binaryAttachments-with-uri-elements.xml"))
      validationResult shouldBe Right(())
    }

    "return validation error when includedBinaryObject and uri elements are both missing" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-missing-uri-and-includedBinaryObject-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Message must contain includedBinaryObject or URI")
      }
    }

    "return validation error when includedBinaryObject and uri elements are both present" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-contains-uri-and-includedBinaryObject-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Message must not contain both includedBinaryObject and URI")
      }
    }

    "return 400 when includedBinaryObject element is 'invalid'" in new Setup {
      val validationResult = verifyElements(readFromFile("uriAndBinaryObject/ie4r02-v2-includedBinaryObject-element-contains-invalid-string.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe "Value of element includedBinaryObject is not valid base 64 data"
      }
    }

    "return validation error when referralRequestReference element is missing" in new Setup {
      val validationResult = verifyElements(readFromFile("referralRequestReference/ie4r02-v2-missing-referralRequestReference-element.xml"))
      validationResult shouldBe Right(())
    }

    "return validation error when referralRequestReference element is blank" in new Setup {
      val validationResult = verifyElements(readFromFile("referralRequestReference/ie4r02-v2-blank-referralRequestReference-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element referralRequestReference is too short")
      }
    }

    "return validation error when referralRequestReference element is too long" in new Setup {
      val validationResult = verifyElements(readFromFile("referralRequestReference/ie4r02-v2-too-long-referralRequestReference-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element referralRequestReference is too long")
      }
    }

    "return validation error when MessageID element is missing" in new Setup {
      val validationResult = verifyElements(readFromFile("messageId/ie4r02-v2-missing-messageId-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Element SOAP Header MessageID is missing")
      }
    }

    "return validation error when MessageID element is blank" in new Setup {
      val validationResult = verifyElements(readFromFile("messageId/ie4r02-v2-blank-messageId-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element SOAP Header MessageID is too short")
      }
    }

    "return validation error when MessageID element is too long" in new Setup {
      val validationResult = verifyElements(readFromFile("messageId/ie4r02-v2-too-long-messageId-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element SOAP Header MessageID is too long")
      }
    }

    "return validation error when filename element is missing" in new Setup {
      val validationResult = verifyElements(readFromFile("filename/ie4r02-v2-missing-filename-element.xml"))
      validationResult shouldBe Right(())
    }

    "return validation error when filename element is blank" in new Setup {
      val validationResult = verifyElements(readFromFile("filename/ie4r02-v2-blank-filename-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element filename is too short")
      }
    }

    "return validation error when filename element is too long" in new Setup {
      val validationResult = verifyElements(readFromFile("filename/ie4r02-v2-too-long-filename-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element filename is too long")
      }
    }

    "return validation error when description element is missing" in new Setup {
      val validationResult = verifyElements(readFromFile("description/ie4r02-v2-missing-description-element.xml"))
      validationResult shouldBe Right(())
    }

    "return validation error when description element is blank" in new Setup {
      val validationResult = verifyElements(readFromFile("description/ie4r02-v2-blank-description-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element description is too short")
      }
    }

    "return validation error when description element is too long" in new Setup {
      val validationResult = verifyElements(readFromFile("description/ie4r02-v2-too-long-description-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element description is too long")
      }
    }

    "return validation error when both MRN and LRN elements are missing" in new Setup {
      val validationResult = verifyElements(readFromFile("MRN/ie4r02-v2-missing-both-LRN-and-MRN-elements.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Element SOAP Body MRN/LRN missing")
      }
    }

    "return validation error when both MRN and LRN elements are present" in new Setup {
      val validationResult = verifyElements(readFromFile("MRN/ie4r02-v2-both-LRN-and-MRN-elements-present.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Message must not contain both MRN and LRN")
      }
    }

    "return validation error when MRN element is blank" in new Setup {
      val validationResult = verifyElements(readFromFile("MRN/ie4r02-v2-blank-MRN-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element MRN is too short")
      }
    }

    "return validation error when MRN element is too long" in new Setup {
      val validationResult = verifyElements(readFromFile("MRN/ie4r02-v2-too-long-MRN-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element MRN is too long")
      }
    }

    "return validation error when MRN element is too short" in new Setup {
      val validationResult = verifyElements(readFromFile("MRN/ie4r02-v2-too-long-MRN-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element MRN is too long")
      }
    }

    "return validation error when LRN element is blank" in new Setup {
      val validationResult = verifyElements(readFromFile("LRN/ie4r02-v2-blank-LRN-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element LRN is too short")
      }
    }

    "return validation error when LRN element is too long" in new Setup {
      val validationResult = verifyElements(readFromFile("LRN/ie4r02-v2-too-long-LRN-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element LRN is too long")
      }
    }

    "return success when MIME element is missing" in new Setup {
      val validationResult = verifyElements(readFromFile("MIME/ie4r02-v2-missing-mime-element.xml"))
      validationResult shouldBe Right(())
    }

    "return validation error when MIME element is blank" in new Setup {
      val validationResult = verifyElements(readFromFile("MIME/ie4r02-v2-blank-mime-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element MIME is too short")
      }
    }

    "return validation error when MIME element is too long" in new Setup {
      val validationResult = verifyElements(readFromFile("MIME/ie4r02-v2-too-long-mime-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element MIME is too long")
      }
    }

    "return missing action error message when action element is missing" in new Setup {
      val validationResult = verifyElements(readFromFile("action/ie4r02-v2-missing-action-element.xml"))

      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Element SOAP Header Action is missing")
      }
    }

    "return validation error when action element is blank" in new Setup {
      val validationResult = verifyElements(readFromFile("action/ie4r02-v2-blank-action-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("SOAP Header Action should contain / character but does not")
      }
    }

    "return validation error when action element is a single slash" in new Setup {
      val validationResult = verifyElements(readFromFile("action/ie4r02-v2-single-slash-action-element.xml"))
      validationResult match {
        case Right(_)                        => fail()
        case Left(nel: NonEmptyList[String]) => nel.toList.head shouldBe ("Value of element SOAP Header Action is too short")
      }
    }
  }
}

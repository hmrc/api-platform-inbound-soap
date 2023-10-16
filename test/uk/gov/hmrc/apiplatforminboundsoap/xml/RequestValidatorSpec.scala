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

import cats.data.NonEmptyList
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import scala.io.Source

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

  "return missing action error message when all elements are valid" in new Setup {
    val validationResult = verifyElements(readFromFile("action/ie4r02-v2-missing-action-element.xml"))

    validationResult match {
      case Right(_) => fail()
      case Left(nel: NonEmptyList[(String, String)]) => nel.toList.head shouldBe ("action", "SOAP Header Action missing")
    }
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




}

}

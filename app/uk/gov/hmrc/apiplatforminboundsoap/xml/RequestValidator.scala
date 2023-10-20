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

import java.util.Base64
import scala.xml.NodeSeq

import cats.data.Validated._
import cats.data._
import cats.implicits._

import play.api.Logging
import uk.gov.hmrc.http.HttpErrorFunctions

trait RequestValidator extends XmlHelper with HttpErrorFunctions with Logging {

  val actionMinLength                   = 3
  val descriptionMinLength              = 1
  val filenameMinLength                 = 1
  val messageIdMinLength                = 1
  val mimeMinLength                     = 1
  val referenceMinLength                = 1
  val referralRequestReferenceMinLength = 1
  val actionMaxLength                   = 9999
  val descriptionMaxLength              = 256
  val filenameMaxLength                 = 256
  val messageIdMaxLength                = 291
  val mimeMaxLength                     = 70
  val referenceMaxLength                = 18
  val referralRequestReferenceMaxLength = 35
  val uriMinLength                      = 10
  val uriMaxLength                      = 64000

  def verifyElements(soapMessage: NodeSeq): Either[cats.data.NonEmptyList[String], Unit] = {
    {
      (
        verifyAttachments(soapMessage),
        verifyActionExists(soapMessage),
        verifyMessageId(soapMessage),
        verifyReferenceNumber(soapMessage),
        verifyAction(soapMessage),
        verifyActionLength(soapMessage)
      )
    }.mapN((_, _, _, _, _, _) => {
      ()
    }).toEither
  }

  private def verifyAttachments(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {

    val allAttachments = getBinaryElements(soapMessage)

    val referralRequestReferenceValidation = if (allAttachments.nonEmpty) verifyReferralRequestReference(soapMessage)
    else Validated.valid(())

    val validateAttachments = allAttachments.map(attachment => verifyAttachment(attachment)).combineAll

    Seq(referralRequestReferenceValidation, validateAttachments).combineAll
  }

  private def verifyAttachment(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    {
      (
        verifyUriOrBinaryObject(soapMessage),
        verifyFilename(soapMessage),
        verifyMime(soapMessage),
        verifyDescription(soapMessage)
      )
    }.mapN((_, _, _, _) =>
      ()
    )
  }

  private def verifyDescription(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    val description = getBinaryDescription(soapMessage)
    verifyAttribute(attributeValue = description, attributeName = "description", minLength = descriptionMinLength, maxLength = descriptionMaxLength)
  }

  private def verifyReferenceNumber(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    (getMRN(soapMessage), getLRN(soapMessage)) match {
      case (None, lrn)                        => verifyAttribute(lrn, "LRN", referenceMinLength, referenceMaxLength, true)
      case (mrn, None)                        => verifyAttribute(mrn, "MRN", referenceMinLength, referenceMaxLength, true)
      case (None, None)                       => Validated.valid(())
      case (Some(_), Some(_))                 => ("message must not contain both MRN and LRN").invalidNel[Unit]
    }
  }

  private def verifyMessageId(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    val messageId = getMessageId(soapMessage)
    verifyAttribute(attributeValue = messageId, attributeName = "SOAP Header MessageID", minLength = messageIdMinLength, maxLength = messageIdMaxLength)
  }

  private def verifyFilename(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    val filename = getBinaryFilename(soapMessage)
    verifyAttribute(attributeValue = filename, attributeName = "filename", minLength = filenameMinLength, maxLength = filenameMaxLength)
  }

  private def verifyMime(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    val mime = getBinaryMimeType(soapMessage)
    verifyAttribute(attributeValue = mime, attributeName = "MIME", minLength = mimeMinLength, maxLength = mimeMaxLength, permitMissing = true)
  }

  private def verifyUriOrBinaryObject(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    def verifyUri(uri: String): ValidatedNel[String, Unit] = {
      verifyAttribute(attributeValue = Some(uri), attributeName = "URI", minLength = uriMinLength, maxLength = uriMaxLength, permitMissing = true)
    }

    def verifyIncludedBinaryObject(includedBinaryObject: String): ValidatedNel[String, Unit] = {
      val failLeft = "Value of element includedBinaryObject is not valid base 64 data".invalidNel[Unit]
      try {
        val decoded = Base64.getDecoder().decode(includedBinaryObject)
        if (decoded.isEmpty) failLeft else Validated.valid(())
      } catch {
        case _: Throwable => {
          logger.warn("Error while trying to decode includedBinaryObject as base 64 data. Perhaps it is not correctly encoded")
          failLeft
        }
      }
    }

    (getBinaryBase64Object(soapMessage), getBinaryUri(soapMessage)) match {
      case (None, Some(uri))                  => verifyUri(uri)
      case (Some(includedBinaryObject), None) => verifyIncludedBinaryObject(includedBinaryObject)
      case (None, None)                       => ("message must contain includedBinaryObject or URI").invalidNel[Unit]
      case (Some(_), Some(_))                 => ("message must not contain both includedBinaryObject and URI").invalidNel[Unit]
    }
  }

  private def verifyReferralRequestReference(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    val referralRequestReference = getReferralRequestReference(soapMessage)
    verifyAttribute(
      attributeValue = referralRequestReference,
      attributeName = "referralRequestReference",
      minLength = referralRequestReferenceMinLength,
      maxLength = referralRequestReferenceMaxLength
    )
  }

  private def verifyActionExists(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    getSoapAction(soapMessage) match {
      case Some(_) => Validated.valid(())
      case None    => "Element SOAP Header Action is missing".invalidNel[Unit]
    }
  }

  private def verifyActionLength(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    getSoapAction(soapMessage) match {
      case None       => Validated.valid(())
      case actionText => verifyStringLength(maybeString = actionText, attributeName = "SOAP Header Action", minLength = actionMinLength, maxLength = actionMaxLength) match {
          case Right(_)      => Validated.valid(())
          case Left(problem) => problem.invalidNel[Unit]
        }
    }
  }

  private def verifyAction(soapMessage: NodeSeq): ValidatedNel[String, Unit] = {
    getSoapAction(soapMessage) match {
      case None             => Validated.valid(())
      case Some(actionText) => if (actionText.contains("/")) {
          Validated.valid(())
        } else {
          ("SOAP Header Action should contain / character but does not").invalidNel[Unit]
        }
    }
  }

  private def verifyAttribute(
      attributeValue: Option[String],
      attributeName: String,
      minLength: Int,
      maxLength: Int,
      permitMissing: Boolean = false
    ): ValidatedNel[String, Unit] = {
    val verifyResult = if (permitMissing) {
      verifyStringLengthPermitMissing(attributeValue, attributeName, minLength, maxLength)
    } else {
      verifyStringLength(attributeValue, attributeName, minLength, maxLength)
    }
    verifyResult match {
      case Right(_)      => Validated.valid(())
      case Left(problem) => problem.invalidNel[Unit]
    }
  }

  private def verifyStringLength(maybeString: Option[String], attributeName: String="", minLength: Int, maxLength: Int): Either[String, Unit] = {
    maybeString match {
      case Some(string) if string.trim.length < minLength => Left(s"Value of element $attributeName is too short")
      case Some(string) if string.length > maxLength      => Left(s"Value of element $attributeName is too long")
      case None                                           => Left(s"Element $attributeName is missing")
      case _                                              => Right(())
    }
  }

  private def verifyStringLengthPermitMissing(maybeString: Option[String], attributeName: String, minLength: Int, maxLength: Int): Either[String, Unit] = {
    maybeString match {
      case Some(string) if string.trim.length < minLength => Left(s"Value of element $attributeName is too short")
      case Some(string) if string.length > maxLength      => Left(s"Value of element $attributeName is too long")
      case _                                              => Right(())
    }
  }

}

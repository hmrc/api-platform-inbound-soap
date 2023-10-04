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

package uk.gov.hmrc.apiplatforminboundsoap.controllers.actionBuilders

import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

import _root_.uk.gov.hmrc.http.HttpErrorFunctions
import cats.data.Validated._
import cats.data._
import cats.implicits._

import play.api.Logging
import play.api.http.Status.BAD_REQUEST
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, Result}
import uk.gov.hmrc.apiplatforminboundsoap.xml.XmlHelper

@Singleton
class SoapMessageValidateAction @Inject() (xmlHelper: XmlHelper)(implicit ec: ExecutionContext)
    extends ActionFilter[Request] with HttpErrorFunctions with Logging {

  case class ValidRequest(
      validDescription: Boolean,
      validFilename: Boolean,
      validMessageId: Boolean,
      validMime: Boolean,
      validReferralRequestReference: Boolean,
      validAction: Boolean,
      validActionLength: Boolean,
      validIncludedBinaryObject: Boolean
    )

  val actionMinLength                   = 3
  val descriptionMinLength              = 1
  val filenameMinLength                 = 1
  val messageIdMinLength                = 1
  val mimeMinLength                     = 1
  val referralRequestReferenceMinLength = 1
  val actionMaxLength                   = 9999
  val descriptionMaxLength              = 256
  val filenameMaxLength                 = 256
  val messageIdMaxLength                = 291
  val mimeMaxLength                     = 70
  val referralRequestReferenceMaxLength = 35

  override def executionContext: ExecutionContext = ec

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {

    val body: NodeSeq = request.body.asInstanceOf[xml.NodeSeq]

    verifyElements(body) match {
      case Right(_) => successful(None)
      case Left(e)  => {
        val statusCode = BAD_REQUEST
        val requestId  = request.headers.get("x-request-id").getOrElse("requestId not known")
        logger.warn(s"RequestID: $requestId")
        logger.warn(mapErrorsToString(e, "Received a request that contained a ", " that was rejected because it "))
        successful(Some(BadRequest(createSoapErrorResponse(statusCode, mapErrorsToString(e, "Argument ", " "), requestId))))
      }
    }
  }

  private def createSoapErrorResponse(statusCode: Int, reason: String, requestId: String) = {
    s"""<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
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

  private def verifyElements(soapMessage: NodeSeq): Either[cats.data.NonEmptyList[(String, String)], ValidRequest] = {
    {
      (
        verifyDescription(soapMessage),
        verifyFilename(soapMessage),
        verifyMessageId(soapMessage),
        verifyMime(soapMessage),
        verifyReferralRequestReference(soapMessage),
        verifyAction(soapMessage),
        verifyActionLength(soapMessage),
        verifyIncludedBinaryObject(soapMessage)
      )
    }.mapN((validDescription, validFilename, validMessageId, validMime, validReferralRequestReference, validAction, validActionLength, validIncludedBinaryObject) => {
      ValidRequest(validDescription, validFilename, validMessageId, validMime, validReferralRequestReference, validAction, validActionLength, validIncludedBinaryObject)
    }).toEither
  }

  private def verifyDescription(soapMessage: NodeSeq): ValidatedNel[(String, String), Boolean] = {
    val description = xmlHelper.getBinaryDescription(soapMessage)
    verifyStringLength(description, descriptionMinLength, descriptionMaxLength) match {
      case Left(problem) => ("description", problem).invalidNel[Boolean]
      case Right(_)      => Validated.valid(true)
    }
  }

  private def verifyMessageId(soapMessage: NodeSeq): ValidatedNel[(String, String), Boolean] = {
    val messageId = xmlHelper.getMessageId(soapMessage)
    verifyStringLength(messageId, messageIdMinLength, messageIdMaxLength) match {
      case Left(problem) => ("messageId", problem).invalidNel[Boolean]
      case Right(_)      => Validated.valid(true)
    }
  }

  private def verifyFilename(soapMessage: NodeSeq): ValidatedNel[(String, String), Boolean] = {
    val filename = xmlHelper.getBinaryFilename(soapMessage)
    verifyStringLength(filename, filenameMinLength, filenameMaxLength) match {
      case Left(problem) => ("filename", problem).invalidNel[Boolean]
      case Right(_)      => Validated.valid(true)
    }
  }

  private def verifyMime(soapMessage: NodeSeq): ValidatedNel[(String, String), Boolean] = {
    val mime = xmlHelper.getBinaryMimeType(soapMessage)
    verifyStringLength(mime, mimeMinLength, mimeMaxLength) match {
      case Right(_)      => Validated.valid(true)
      case Left(problem) => ("MIME", problem).invalidNel[Boolean]
    }
  }

  private def verifyReferralRequestReference(soapMessage: NodeSeq): ValidatedNel[(String, String), Boolean] = {
    val referralRequestReference = xmlHelper.getReferralRequestReference(soapMessage)
    verifyStringLength(referralRequestReference, referralRequestReferenceMinLength, referralRequestReferenceMaxLength) match {
      case Right(_)      => Validated.valid(true)
      case Left(problem) => ("referralRequestReference", problem).invalidNel[Boolean]
    }
  }

  private def verifyActionLength(soapMessage: NodeSeq): ValidatedNel[(String, String), Boolean] = {
    val action = xmlHelper.getSoapAction(soapMessage)
    verifyStringLength(action, actionMinLength, actionMaxLength) match {
      case Right(_)      => Validated.valid(true)
      case Left(problem) => ("action", problem).invalidNel[Boolean]
    }
  }

  private def verifyAction(soapMessage: NodeSeq): ValidatedNel[(String, String), Boolean] = {
    val action = xmlHelper.getSoapAction(soapMessage)
    if (action.contains("/")) {
      Validated.valid(true)
    } else {
      ("action", "should contain / character but does not").invalidNel[Boolean]
    }
  }

  private def verifyIncludedBinaryObject(soapMessage: NodeSeq): ValidatedNel[(String, String), Boolean] = {
    val failLeft             = ("includedBinaryObject", "is not valid base 64 data").invalidNel[Boolean]
    val includedBinaryObject = xmlHelper.getBinaryBase64Object(soapMessage)
    try {
      val decoded = Base64.getDecoder().decode(includedBinaryObject)
      if (decoded.isEmpty) failLeft else Validated.valid(true)
    } catch {
      case _: Throwable => {
        logger.warn("Error while trying to decode includedBinaryObject as base 64 data. Perhaps it is not correctly encoded")
        failLeft
      }
    }
  }

  private def verifyStringLength(string: String, minLength: Int, maxLength: Int): Either[String, Boolean] = {
    if (string.trim.length < minLength)
      Left("is too short")
    else if (string.length > maxLength)
      Left("is too long")
    else Right(true)
  }

  private def mapErrorsToString(errorList: NonEmptyList[(String, String)], fieldName: String, problem: String): String = {
    val flatListErrors: List[(String, String)] = errorList.toList
    flatListErrors.map(problemDescription => s"$fieldName${problemDescription._1}$problem${problemDescription._2}").mkString("\n")
  }

}

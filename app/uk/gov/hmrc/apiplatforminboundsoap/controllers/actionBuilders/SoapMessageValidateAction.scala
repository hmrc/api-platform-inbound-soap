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

import _root_.uk.gov.hmrc.http.HttpErrorFunctions
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.http.Status.BAD_REQUEST
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, Result}
import uk.gov.hmrc.apiplatforminboundsoap.xml.XmlHelper

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

@Singleton
class SoapMessageValidateAction @Inject() (xmlHelper: XmlHelper)(implicit ec: ExecutionContext)
    extends ActionFilter[Request] with HttpErrorFunctions with Logging {
  override def executionContext: ExecutionContext                                         = ec

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    val body: NodeSeq = request.body.asInstanceOf[xml.NodeSeq]

    verifyElements(body) match {
      case Right(_)    => successful(None)
      case Left(problemDescription) => {
        logger.warn(s"Received a request that contained a ${problemDescription._1} that was ${problemDescription._2}")
        val statusCode = BAD_REQUEST
        val requestId  = request.headers.get("x-request-id").getOrElse("requestId not known")
        val reason     = s"Argument ${problemDescription._1} ${problemDescription._2}"
        successful(Some(BadRequest(createSoapErrorResponse(statusCode, reason, requestId))))
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

  private def verifyElements(soapMessage: NodeSeq): Either[(String, String), Boolean] = {
    val descriptionMaxLength                                                        = 256
    val filenameMaxLength                                                           = 256
    val mimeMaxLength                                                               = 70
    val referralRequestReferenceMaxLength                                           = 35
    def verifyStringLength(string: String, maxLength: Int): Either[String, Boolean] = {
      if (string.trim.isEmpty) Left("too short")
      else if (string.length > maxLength) Left("too long")
      else Right(true)
    }
    def verifyDescription(soapMessage: NodeSeq): Either[(String, String), Boolean]  = {
      val description = xmlHelper.getBinaryDescription(soapMessage)
      verifyStringLength(description, descriptionMaxLength) match {
        case Left(problem) => Left("description", problem)
        case Right(_)      => Right(true)
      }
    }

    def verifyFilename(soapMessage: NodeSeq): Either[(String, String), Boolean] = {
      val filename = xmlHelper.getBinaryFilename(soapMessage)
      verifyStringLength(filename, filenameMaxLength) match {
        case Left(problem) => Left("filename", problem)
        case Right(_)      => Right(true)
      }
    }

    def verifyMime(soapMessage: NodeSeq): Either[(String, String), Boolean] = {
      val mime = xmlHelper.getBinaryMimeType(soapMessage)
      verifyStringLength(mime, mimeMaxLength) match {
        case Left(problem) => Left("MIME", problem)
        case Right(_)      => Right(true)
      }
    }

    def verifyReferralRequestReference(soapMessage: NodeSeq): Either[(String, String), Boolean] = {
      val referralRequestReference = xmlHelper.getReferralRequestReference(soapMessage)
      verifyStringLength(referralRequestReference, referralRequestReferenceMaxLength) match {
        case Left(problem) => Left("referralRequestReference", problem)
        case Right(_)      => Right(true)
      }
    }

    def verifyIncludedBinaryObject(soapMessage: NodeSeq): Either[(String, String), Boolean] = {
      val failLeft = Left("includedBinaryObject","is not valid base 64 data")
      val includedBinaryObject = xmlHelper.getBinaryObject(soapMessage)
      try {
        val decoded = Base64.getDecoder().decode(includedBinaryObject)
        logger.warn(s"Decoded base 64 string is $decoded")
        if (decoded.isEmpty) failLeft else Right(true)
      } catch{
        case _:Throwable => failLeft
      }
    }

    verifyDescription(soapMessage) match {
      case Right(_)    => verifyFilename(soapMessage) match {
          case Right(_)    => verifyMime(soapMessage) match {
              case Right(_)    => verifyReferralRequestReference(soapMessage) match {
                case Right(_) => verifyIncludedBinaryObject(soapMessage) match {
                  case Right(_) => Right(true)
                  case Left(value) => Left(value)
                }
                case Left(value) => Left(value)
              }
              case Left(value) => Left(value)
            }
          case Left(value) => Left(value)
        }
      case Left(value) => Left(value)
    }
  }
}

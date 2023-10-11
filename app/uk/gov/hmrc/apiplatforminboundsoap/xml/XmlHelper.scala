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

import javax.inject.Singleton
import scala.xml.{Node, NodeSeq}

import uk.gov.hmrc.apiplatforminboundsoap.models._

@Singleton
class XmlHelper {

  def getMessageVersion(soapMessage: NodeSeq): SoapMessageVersion = {
    def getVersionTwoNamespace(soapMessage: NodeSeq): SoapMessageVersion = {
      soapMessage.map((n: Node) => Option(n.getNamespace("v2"))).exists(ns => ns.nonEmpty) match {
        case true  => Version2
        case false => VersionNotRecognised
      }
    }

    def isVersionOneNamespace(soapMessage: NodeSeq): Boolean = {
      val body: NodeSeq = soapMessage \ "Body"
      body.map((n: Node) => n.descendant.toString.contains("ns1")).contains(true)
    }

    isVersionOneNamespace(soapMessage) match {
      case true  => Version1
      case false => getVersionTwoNamespace(soapMessage)
    }
  }

  def getSoapAction(soapMessage: NodeSeq): Option[String] = {
    val action = (soapMessage \\ "Action")
    if (action.isEmpty) None else Some(action.text)
  }

  def getMessageId(soapMessage: NodeSeq): Option[String] = {
    val messageId = soapMessage \\ "messageId"
    if (messageId.isEmpty) None else Some(messageId.text)
  }

  def isFileAttached(soapMessage: NodeSeq): Boolean = {
    getBinaryAttachment(soapMessage).nonEmpty || getBinaryFile(soapMessage).nonEmpty
  }

  private def getBinaryAttachment(soapMessage: NodeSeq): NodeSeq = {
    soapMessage \\ "binaryAttachment"
  }

  private def getBinaryFile(soapMessage: NodeSeq): NodeSeq = {
    soapMessage \\ "binaryFile"
  }

  private def getBinaryElement(soapMessage: NodeSeq): NodeSeq = {
    val attachment = getBinaryAttachment(soapMessage)
    if (attachment.isEmpty) getBinaryFile(soapMessage) else attachment
  }

  def getBinaryFilename(soapMessage: NodeSeq): String = {
    (getBinaryElement(soapMessage) \\ "filename").text
  }

  def getBinaryMimeType(soapMessage: NodeSeq): String = {
    (getBinaryElement(soapMessage) \\ "MIME").text
  }

  def getReferralRequestReference(soapMessage: NodeSeq): String = {
    (soapMessage \\ "referralRequestReference").text
  }

  def getBinaryDescription(soapMessage: NodeSeq): String = {
    (getBinaryElement(soapMessage) \\ "description").text
  }

  def getBinaryBase64Object(soapMessage: NodeSeq): Option[String] = {
    val includedBinaryObject = (getBinaryElement(soapMessage) \\ "includedBinaryObject")
    if (includedBinaryObject.isEmpty) None else Some(includedBinaryObject.text)
  }

  def getBinaryUri(soapMessage: NodeSeq): Option[String] = {
    val binaryElementUri = getBinaryElement(soapMessage) \\ "URI"
    if (binaryElementUri.isEmpty) None else Some(binaryElementUri.text)
  }

  def hasUriForAttachment(soapMessage: NodeSeq): Boolean = {
    getBinaryUri(soapMessage).isDefined
  }

  def getReferenceNumber(soapMessage: NodeSeq): Option[String] = {
    val mrn = soapMessage \\ "MRN"
    val lrn = soapMessage \\ "LRN"
    if (mrn.isEmpty && lrn.isEmpty) None
    else if (mrn.isEmpty) {
      Some(lrn.text)
    } else {
      Some(mrn.text)
    }
  }
}

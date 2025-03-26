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

import scala.annotation.tailrec
import scala.xml.{Elem, NodeSeq, Text}

import jstengel.ezxml.core.SimpleWrapper.ElemWrapper

import uk.gov.hmrc.apiplatforminboundsoap.util.{ApplicationLogger, Base64Encoder}

trait Ics2XmlHelper extends ApplicationLogger with Base64Encoder {

  def getSoapAction(soapMessage: NodeSeq): Option[String] = {
    val action = soapMessage \\ "Action"
    if (action.isEmpty) None else Some(action.text)
  }

  def getMessageId(soapMessage: NodeSeq): Option[String] = {
    val messageId = soapMessage \\ "MessageID"
    if (messageId.isEmpty) None else Some(messageId.text)
  }

  def isFileIncluded(soapMessage: NodeSeq): Boolean = {
    getBinaryAttachment(soapMessage).nonEmpty || getBinaryFile(soapMessage).nonEmpty
  }

  def getBinaryElementsWithEmbeddedData(soapMessage: NodeSeq): NodeSeq = {
    def notContainsUrl(nodeSeq: NodeSeq) = {
      (nodeSeq \\ "URI").isEmpty
    }
    getBinaryElements(soapMessage) takeWhile (notContainsUrl(_))
  }

  def getBinaryElements(soapMessage: NodeSeq): NodeSeq = {
    getBinaryAttachment(soapMessage) ++ getBinaryFile(soapMessage)
  }

  private def getBinaryAttachment(soapMessage: NodeSeq): NodeSeq = {
    soapMessage \\ "binaryAttachment"
  }

  private def getBinaryFile(soapMessage: NodeSeq): NodeSeq = {
    soapMessage \\ "binaryFile"
  }

  def getBinaryFilename(binaryBlock: NodeSeq): Option[String] = {
    val filename = binaryBlock \\ "filename"
    if (filename.isEmpty) None else Some(filename.text)
  }

  def getBinaryMimeType(binaryBlock: NodeSeq): Option[String] = {
    val mime = binaryBlock \\ "MIME"
    if (mime.isEmpty) None else Some(mime.text)
  }

  def getReferralRequestReference(soapMessage: NodeSeq): Option[String] = {
    val referralRequestReference = soapMessage \\ "referralRequestReference"
    if (referralRequestReference.isEmpty) None else Some(referralRequestReference.text)
  }

  def getBinaryDescription(binaryBlock: NodeSeq): Option[String] = {
    val description = binaryBlock \\ "description"
    if (description.isEmpty) None else Some(description.text)
  }

  def getBinaryBase64Object(binaryBlock: NodeSeq): Option[String] = {
    val includedBinaryObject = binaryBlock \\ "includedBinaryObject"
    if (includedBinaryObject.isEmpty) None else Some(includedBinaryObject.text)
  }

  def replaceEmbeddedAttachments(replacement: Map[String, String], completeXML: NodeSeq, encodeReplacement: Boolean = false): Either[Set[String], NodeSeq] = {
    val xmlElem                       = completeXML.asInstanceOf[Elem]
    def addForAttrs(elem: Elem): Elem = {
      @tailrec
      def add(targets: List[String], elem: Elem): Elem = {
        targets match {
          case Nil       => elem
          case x :: tail =>
            add(tail, (elem \\~ x transformTargetRoot (n => n.setAttribute("for", n.filterChildren(c => c.label == "filename").text))).getOrElse(elem))
        }
      }
      add(List("binaryAttachment", "binaryFile"), elem)
    }

    def replaceAllBinaryObjects(e: Elem, filename: String, replacement: String): Either[String, Elem] = {
      def replaceText(elem: Elem, x: String): Elem = {
        (elem \\~ (x, _ \@ "for" == filename)) mapChildren {
          case e: Elem =>
            if (e.label == "includedBinaryObject") e.copy(child = if (encodeReplacement) new Text(encode(replacement)) else new Text(replacement)) else e
          case n       => n
        }
      }.getOrElse(elem)

      @tailrec
      def replaceBinaryObject(targets: List[String], elem: Elem): Elem = {
        targets match {
          case Nil       => elem
          case x :: tail =>
            replaceBinaryObject(tail, replaceText(elem, x))
        }
      }
      val transformed                                                  = replaceBinaryObject(List("binaryAttachment", "binaryFile"), e)
      if (transformed == e) Left(filename) else Right(transformed)
    }

    def doReplace(r: Map[String, String], elem: Elem): Either[String, Elem] = {
      if (r.isEmpty) Right(elem)
      else {
        replaceAllBinaryObjects(elem, r.head._1, r.head._2) match {
          case Right(e) => doReplace(r.tail, e)
          case _        => Left(r.head._1)
        }
      }
    }

    def removeForLabels(elem: Elem): Elem = {
      @tailrec
      def remove(targets: List[String], elem: Elem): Elem = {
        targets match {
          case Nil       => elem
          case x :: tail =>
            remove(tail, (elem \\~ x transformTargetRoot (e => e.copy(attributes = e.attributes.remove("for")))).getOrElse(elem))
        }
      }
      remove(List("binaryAttachment", "binaryFile"), elem)
    }

    val withForFileAttrs    = addForAttrs(xmlElem)
    val attachmentsReplaced = doReplace(replacement, withForFileAttrs)
    attachmentsReplaced match {
      case Right(elem)            => Right(removeForLabels(elem).asInstanceOf[NodeSeq])
      case Left(notFoundFilename) => Left(Set(notFoundFilename))
    }
  }

  def getBinaryUri(binaryBlock: NodeSeq): Option[String] = {
    val binaryElementUri = binaryBlock \\ "URI"
    if (binaryElementUri.isEmpty) None else Some(binaryElementUri.text)
  }

  def getMRN(soapMessage: NodeSeq): Option[String] = {
    val mrn = soapMessage \\ "MRN"
    if (mrn.isEmpty) None else Some(mrn.text)
  }

  def getLRN(soapMessage: NodeSeq): Option[String] = {
    val lrn = soapMessage \\ "LRN"
    if (lrn.isEmpty) None else Some(lrn.text)
  }
}

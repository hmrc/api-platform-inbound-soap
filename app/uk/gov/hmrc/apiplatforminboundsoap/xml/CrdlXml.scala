/*
 * Copyright 2025 HM Revenue & Customs
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

import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, NodeSeq, Text}

import advxml.implicits.*
import advxml.transform.XmlModifier.*
import advxml.transform.XmlRule
import advxml.transform.XmlZoom.root

import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

class NoChangeTransformer extends XmlTransformer {

  override def replaceAttachment: (NodeSeq, String) => NodeSeq = {
    (n, _) => n
  }
}

class CrdlAttachmentReplacingTransformer extends XmlTransformer {

  def replaceAttachment: (NodeSeq, String) => NodeSeq = {
    (m, s) =>
      // TODO this implementation has us closely tied to the structure of the document whereas before we searched for the attachment XML element at any point in the document
      // TODO this introduces a risk that we don't continue finding the attachment in all documents - only those matching the structure of the solitary example we've been given.
      // Make this search for the attachment element as lax as before
      val rule: XmlRule = root.Body.ReceiveReferenceDataReqMsg.ReceiveReferenceDataRequestResult ==> Replace(ns => doTransform(ns, s))
      m.transform[Try](rule) match {
        case Failure(_)     => m
        case Success(value) => value
      }
  }

  def doTransform(ns: NodeSeq, replacement: String): NodeSeq = ns.theSeq.map(n =>
    n match {
      case elem: Elem if elem.label == "ReceiveReferenceDataRequestResult" =>
        elem.copy(child = elem.child collect {
          case Text(_) => Text(replacement)
        })
      case n                                                               =>
        n
    }
  )
}

trait CrdlXml extends ApplicationLogger {

  def getBinaryAttachment(soapMessage: NodeSeq): NodeSeq = {
    soapMessage \\ "ReceiveReferenceDataRequestResult"
  }

  def fileIncluded(soapMessage: NodeSeq): Boolean = {
    (soapMessage \\ "ReceiveReferenceDataRequestResult").nonEmpty
  }

  def taskIdentifier(wholeMessage: NodeSeq): Option[String] = {
    val taskIdentifier = wholeMessage \\ "TaskIdentifier"
    taskIdentifier.map(n => n.text).headOption
  }

  def xmlTransformer: XmlTransformer = new CrdlAttachmentReplacingTransformer()

}

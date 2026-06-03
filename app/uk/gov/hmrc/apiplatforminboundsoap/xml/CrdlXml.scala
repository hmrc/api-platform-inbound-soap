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

import advxml.implicits._
import advxml.transform.XmlModifier._
import advxml.transform.XmlRule
import advxml.transform.XmlZoom.root
import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

import scala.util.Try
import scala.xml.{Elem, NodeSeq, Text}

class NoChangeTransformer extends XmlTransformer {

  override def replaceAttachment: (NodeSeq, String) => NodeSeq = {
    (n, _) => n
  }
}

class CrdlAttachmentReplacingTransformer extends XmlTransformer {

  def replaceAttachment: (NodeSeq, String) => NodeSeq = {
    (m, s) =>
      val rule: XmlRule = root.Body.ReceiveReferenceDataReqMsg.ReceiveReferenceDataRequestResult ==> Replace(ns => doTransform(ns, s))
      m.asInstanceOf[Elem].transform[Try](rule).get
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

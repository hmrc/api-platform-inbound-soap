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

import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node, NodeSeq, Text}

import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

class CertexAttachmentReplacingTransformer extends XmlTransformer {

  def replaceAttachment: (NodeSeq, String) => NodeSeq = {
    (m, s) =>
      {
        def rewrite = new RewriteRule {
          override def transform(n: Node): Seq[Node] = n match {
            case elem: Elem if elem.label == "pcaDocumentPdf" =>
              elem.copy(child = elem.child collect {
                case Text(_) => Text(s)
              })
            case n                                            =>
              n
          }
        }

        val transform = new RuleTransformer(rewrite)
        m.map(n => transform(n))
      }
  }
}

trait CertexXml extends ApplicationLogger {

  def getBinaryAttachment(soapMessage: NodeSeq): NodeSeq = {
    soapMessage \\ "pcaDocumentPdf"
  }

  def fileIncluded(soapMessage: NodeSeq): Boolean = {
    getBinaryAttachment(soapMessage).nonEmpty
  }

  def getMrn(soapMessage: NodeSeq): String = {
    (soapMessage \\ "MRN") match {
      case NodeSeq.Empty => ""
      case n: NodeSeq    => n.text
    }
  }

  def getMessageId(soapMessage: NodeSeq): Option[String] = {
    ((soapMessage \@ "messageId").trim, soapMessage \\ "messageId") match {
      case (_, n: NodeSeq) if n != NodeSeq.Empty => Some(n.text)
      case (m, _) if m.nonEmpty                  => Some(m)
      case (_, _)                                => Option.empty
    }
  }

  def xmlTransformer: XmlTransformer = new CertexAttachmentReplacingTransformer()
}

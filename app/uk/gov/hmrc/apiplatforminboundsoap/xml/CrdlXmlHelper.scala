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

import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node, NodeSeq, Text}

import uk.gov.hmrc.apiplatforminboundsoap.util.ApplicationLogger

trait CrdlXmlHelper extends ApplicationLogger {

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

  def replaceAttachment(wholeMessage: NodeSeq, replacement: String): NodeSeq = {
    def rewrite   = new RewriteRule {
      override def transform(n: Node): Seq[Node] = n match {
        case elem: Elem if elem.label == "ReceiveReferenceDataRequestResult" =>
          elem.copy(child = elem.child collect {
            case Text(_) => Text(replacement)
          })
        case n                                                               =>
//        println(s"Node is $n")
          n
      }
    }
//    println(s"Before transformation message is $wholeMessage")
    val transform = new RuleTransformer(rewrite)
    val msg       = wholeMessage.map(n => transform(n))
//    println(s"After transformation message is $msg")
    msg
  }
}

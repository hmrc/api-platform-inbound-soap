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

//class CrdlXmlTransformer extends CrdlXml {
/* def replaceAttachment: (NodeSeq, String) => NodeSeq = {
    (m, s) => {
      def rewrite = new RewriteRule {
        override def transform(n: Node): Seq[Node] = n match {
          case elem: Elem if elem.label == "ReceiveReferenceDataRequestResult" =>
            elem.copy(child = elem.child collect {
              case Text(_) => Text(s)
            })
          case n =>
            n
        }
      }

      val transform = new RuleTransformer(rewrite)
      m.map(n => transform(n))
    }
  }*/

/*class CrdlXmlHelper extends CrdlXml with ApplicationLogger {

/*

  def replaceAttachment(wholeMessage: NodeSeq, replacement: String): NodeSeq = {
    def rewrite   = new RewriteRule {
      override def transform(n: Node): Seq[Node] = n match {
        case elem: Elem if elem.label == "ReceiveReferenceDataRequestResult" =>
          elem.copy(child = elem.child collect {
            case Text(_) => Text(replacement)
          })
        case n                                                               =>
          n
      }
    }
    val transform = new RuleTransformer(rewrite)
    wholeMessage.map(n => transform(n))
  }
 */


}*/

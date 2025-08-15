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

abstract class CrdlXmlFunctions(xmlTransformer: XmlTransformer) {

  /*def getBinaryAttachment(soapMessage: NodeSeq): NodeSeq = {
    soapMessage \\ "ReceiveReferenceDataRequestResult"
  }

  def fileIncluded(soapMessage: NodeSeq): Boolean = {
    (soapMessage \\ "ReceiveReferenceDataRequestResult").nonEmpty
  }

  def taskIdentifier(wholeMessage: NodeSeq): Option[String] = {
    val taskIdentifier = wholeMessage \\ "TaskIdentifier"
    taskIdentifier.map(n => n.text).headOption
  }

  def xmlTransformer: XmlTransformer

  def replaceAttachment(wholeMessage: NodeSeq, replacement: String): NodeSeq = {
    def rewrite = xmlTransformer.rewriteRule


    val transform = xmlTransformer.transformer(rewrite)
     wholeMessage.map(n => transform(n))

  }*/
}

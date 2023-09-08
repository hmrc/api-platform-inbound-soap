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

@Singleton
class XmlHelper {

  def getMessageVersion(soapMessage: NodeSeq) = {
    def isVersionTwoNamespace(soapMessage: NodeSeq): Boolean = {
      soapMessage.map((n: Node) => Option(n.getNamespace("v2"))).filter(ns => ns.nonEmpty).nonEmpty
    }

    def isVersionOneNamespace(soapMessage: NodeSeq): Boolean = {
      val body: NodeSeq = soapMessage \ "Body"
      body.map((n: Node) => n.descendant.toString.contains("ns1")).contains(true)

    }

//TODO find a nicer way of doing this - using a match?
    if (isVersionOneNamespace(soapMessage)) "v1"
    else if (isVersionTwoNamespace(soapMessage)) "v2"
    else
      "Not recognised"

  }
  def getSoapAction(soapMessage: NodeSeq): String = {
    (soapMessage \\ "Action").text
  }

  def getMessageId(soapMessage: NodeSeq): String = {
     (soapMessage  \\ "messageId").text
  }

  def isFileAttached(soapMessage: NodeSeq): Boolean = {
     (soapMessage  \\ "binaryAttachment").nonEmpty || (soapMessage  \\ "binaryFile").nonEmpty
  }
}

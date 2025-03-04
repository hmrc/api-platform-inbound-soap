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

import jstengel.ezxml.core.SimpleWrapper.ElemWrapper
import jstengel.ezxml.core.{OptionalPath, XmlPath, XmlPathList}
import uk.gov.hmrc.apiplatforminboundsoap.util.{ApplicationLogger, Base64Encoder}

import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node, NodeSeq, Text}

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

  def replaceEmbeddedAttachments2(replacement: Map[String, String], completeXML: NodeSeq, encodeReplacement: Boolean = false): Either[Set[String], NodeSeq]= {
    val xmlElem = completeXML.asInstanceOf[Elem]
    val binaryFiles = xmlElem \\~ "binaryFile"
    val binaryAttachments = xmlElem \\~ "binaryAttachment"
    val op: OptionalPath = (binaryAttachments.oPath,binaryFiles.oPath) match {
      //only binaryFile element
      case (None, Some(bf)) => OptionalPath(bf)
      //only binaryAttachment element
      case (Some(ba), None) => OptionalPath(ba)
      //binaryAttachment and binaryFile element
      case (Some(ba:XmlPath), Some(bf:XmlPath)) => OptionalPath(List(ba, bf))
      //multiple binaryAttachment and multiple binaryFile elements
      case (Some(bf:XmlPathList), Some(ba:XmlPathList)) => OptionalPath(bf.paths ++ ba.paths)
      //multiple binaryAttachment and single binaryFile elements
      case (Some(bf:XmlPathList), Some(ba:XmlPath)) => OptionalPath(bf.paths.appended(ba))
      //single binaryAttachment and multiple binaryFile elements
       case (Some(bf:XmlPath), Some(ba:XmlPathList)) => OptionalPath(ba.paths.appended(bf))
      case _ => logger.error("Unable to process XML document binaryFile and binaryAttachment")
        OptionalPath(List.empty[XmlPath])
    }
    val parent: Elem = op match {
      case found: OptionalPath => found.oPath match {
        case Some(walkableXmlPath: XmlPathList) =>
          //all have the same parent so any child will do
          walkableXmlPath.paths.map(p => p.parentElem).last
        case Some(walkableXmlPath: XmlPath) =>
          walkableXmlPath.parentElem
        case _ => logger.error("Unable to process XML document binaryFile and binaryAttachment")
        <help>panic</help>
      }
    }

    val binFileElemsListWithReplacements3: List[Either[Tuple2[String,String], Option[Elem]]] = replacement.map { r=>
      op \\~ ("filename", e => e.text == r._1) match {
        case path: OptionalPath =>
          path.oPath match {
            case Some(pathToFilename: XmlPath) =>
              val replacementText = if (encodeReplacement) encode(r._2) else r._2
              Right(pathToFilename.parentElem \\~ "includedBinaryObject" transformTargetRoot {e:Elem => e.copy(child = new Text(replacementText)) })
            case _ => logger.error(s"Unable to find filename $r._1 in the body but we have a UUID for it")
              Left(r)
          }
            case _ => logger.error(s"Unable to find filename $r._1 in the body but we have a UUID for it")
        Left(r)
      }
    }.toList

    val fileElems3: List[Option[Elem]] = binFileElemsListWithReplacements3.filter(e => e.isRight).map(r => r.getOrElse(Some(<elem>blah</elem>)))

    val binElemsDeleted: Option[Elem] =  xmlElem \\~ s"${parent.label}" deleteChildren  (c => c.label=="binaryFile"||c.label=="binaryAttachment")

    val moddedBinElemsAdded =  binElemsDeleted.map(e=>e \\~ s"${parent.label}" addChildren (fileElems3.filter(e=>e.nonEmpty).map(_.get):_*))

    if (binFileElemsListWithReplacements3.filter(e => e.isLeft).nonEmpty) {
      Left(replacement.keySet)
    }else {
      moddedBinElemsAdded.flatten match {
        case Some(xmlDoc) => Right(xmlDoc.asInstanceOf[NodeSeq])
        case _ => Left(replacement.keySet)
      }
    }
  }

  def replaceEmbeddedAttachments(replacement: Map[String, String], completeXML: NodeSeq, encodeReplacement: Boolean = false): Either[Set[String], NodeSeq] = {
    object replaceIncludedBinaryObject extends RewriteRule {
      override def transform(n: Node): Seq[Node] =
        n match {
          case e: Elem if e.label == "binaryFile" || e.label == "binaryAttachment" =>
            val filename = (n \\ "filename").text
            replacement.get(filename) match {
              case Some(uuid) => if (encodeReplacement) replaceBinaryBase64Object(n, encode(uuid)) else replaceBinaryBase64Object(n, uuid)
              case None       =>
                logger.warn(s"Found a filename [$filename] for which we have no UUID to replace its body")
                n
            }
          case _                                                                   =>
            n
        }
    }
    object transform                   extends RuleTransformer(replaceIncludedBinaryObject)
    val transformed = transform(completeXML.asInstanceOf[Elem])
    if (transformed == completeXML) Left(replacement.keySet) else Right(transformed)
  }

  private def replaceBinaryBase64Object(binaryBlock: NodeSeq, replacement: String): NodeSeq = {
    object replaceIncludedBinaryObject extends RewriteRule {
      override def transform(n: Node): Seq[Node] =
        n match {
          case Elem(_, "includedBinaryObject", _, _, _*) =>
            n.asInstanceOf[Elem].copy(child = List(Text(replacement)))
          case _                                         =>
            n
        }
    }
    object transform                   extends RuleTransformer(replaceIncludedBinaryObject)
    transform(binaryBlock.asInstanceOf[Node])
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

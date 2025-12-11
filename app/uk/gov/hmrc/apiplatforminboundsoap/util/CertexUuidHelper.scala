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

package uk.gov.hmrc.apiplatforminboundsoap.util

import java.util.UUID
import scala.util.matching.Regex

abstract class UuidGenerator {
  def generateRandomUuid: String

  def isValidUuid(candidate: String): Boolean = {
    try {
      UUID.fromString(candidate)
      true
    } catch {
      case _: Throwable =>
        false
    }
  }
}

class RandomUuidGenerator extends UuidGenerator {

  override def generateRandomUuid: String =
    UUID.randomUUID().toString
}

class StaticUuidGenerator extends UuidGenerator {
  override def generateRandomUuid: String = "c23823ba-34cd-4d32-894a-0910e6007557"
}

trait CertexUuidHelper extends ApplicationLogger {

  def uuidFromMessageId(messageId: String): Either[String, String] = {
    val uuidMatch: Regex = "CDCM\\|CTX\\|(.*)".r
    uuidMatch.findFirstMatchIn(messageId).map(m => m.group(1)) match {
      case Some(value) if uuidGenerator.isValidUuid(value) => Right(value)
      case Some(value)                                     =>
        logger.warn(s"Supplied messageId $messageId yielded $value which is not a valid UUID so generating a random one")
        Left(uuidGenerator.generateRandomUuid)
      case None                                            =>
        logger.warn(s"Supplied messageId $messageId did not yield a UUID so generating a random one")
        Left(uuidGenerator.generateRandomUuid)
    }
  }
  def uuidGenerator: UuidGenerator
}

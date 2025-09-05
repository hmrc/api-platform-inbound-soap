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

class UuidHelper extends ApplicationLogger {

  def isValidUuid(candidate: String): Boolean = {
    try {
      UUID.fromString(candidate)
      true
    } catch {
      case _: Throwable =>
        logger.warn(s"Provided UUID [$candidate] does not appear to be a valid UUID")
        false
    }
  }

  def randomUuid(): String = {
    UUID.randomUUID().toString
  }
}

class StaticUuidGenerator extends UuidHelper {
  override def randomUuid(): String = "c23823ba-34cd-4d32-894a-0910e6007557"
}

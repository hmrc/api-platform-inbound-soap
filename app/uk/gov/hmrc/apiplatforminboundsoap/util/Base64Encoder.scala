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

package uk.gov.hmrc.apiplatforminboundsoap.util

import java.util.Base64
import scala.util.matching.Regex

trait Base64Encoder {
  def encode(toEncode: String): String = Base64.getEncoder.encodeToString(toEncode.getBytes)

  def isBase64(candidate: String): Boolean = {
    val pattern: Regex = "^[A-Za-z0-9+/]+={0,2}$".r
    pattern.findFirstMatchIn(candidate) match {
      case Some(r) if r.matched.length % 4 == 0 => true
      case _ => false
    }
  }
}

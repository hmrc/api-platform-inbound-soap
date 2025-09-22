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

package uk.gov.hmrc.apiplatforminboundsoap.config

import java.time.Clock

import com.auth0.jwt.interfaces.JWTVerifier
import com.google.inject.AbstractModule
import com.google.inject.name.Names

import uk.gov.hmrc.apiplatforminboundsoap.util.{ZonedCurrentDTHelper, ZonedDateTimeHelper}
import uk.gov.hmrc.apiplatforminboundsoap.xml.{CertexAttachmentReplacingTransformer, CrdlAttachmentReplacingTransformer, XmlTransformer}

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[JWTVerifier]).toProvider(classOf[JWTVerifierProvider])
    bind(classOf[AppConfig]).asEagerSingleton()
    bind(classOf[Clock]).toInstance(Clock.systemUTC())
    bind(classOf[ZonedDateTimeHelper]).toInstance(new ZonedCurrentDTHelper())
    bind(classOf[XmlTransformer]).annotatedWith(Names.named("crdl")) toInstance new CrdlAttachmentReplacingTransformer()
    bind(classOf[XmlTransformer]).annotatedWith(Names.named("certex")) toInstance new CertexAttachmentReplacingTransformer()
  }
}

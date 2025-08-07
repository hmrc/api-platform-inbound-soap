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

import javax.inject.{Inject, Provider, Singleton}

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import uk.gov.hmrc.apiplatforminboundsoap.connectors.SdesConnector.{Crdl, Ics2}
import uk.gov.hmrc.apiplatforminboundsoap.connectors.{ApiPlatformOutboundSoapConnector, CrdlOrchestratorConnector, ImportControlInboundSoapConnector, SdesConnector}

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): List[Binding[_]] = {

    List(
      bind[ApiPlatformOutboundSoapConnector.Config].toProvider[ApiPlatformOutboundSoapConnectorConfigProvider],
      bind[SdesConnector.Config].toProvider[SdesConnectorConfigProvider],
      bind[ImportControlInboundSoapConnector.Config].toProvider[ImportControlInboundSoapConnectorConfigProvider],
      bind[CrdlOrchestratorConnector.Config].toProvider[CrdlOrchestratorConnectorConfigProvider]
    )
  }
}

@Singleton
class ApiPlatformOutboundSoapConnectorConfigProvider @Inject() (val configuration: Configuration)
    extends ServicesConfig(configuration)
    with Provider[ApiPlatformOutboundSoapConnector.Config] {

  override def get(): ApiPlatformOutboundSoapConnector.Config = {
    val url = baseUrl("api-platform-outbound-soap")
    ApiPlatformOutboundSoapConnector.Config(url)
  }
}

@Singleton
class CrdlOrchestratorConnectorConfigProvider @Inject() (val configuration: Configuration)
    extends ServicesConfig(configuration)
    with Provider[CrdlOrchestratorConnector.Config] {

  override def get(): CrdlOrchestratorConnector.Config = {
    val url = baseUrl("crdl-orchestrator")
    CrdlOrchestratorConnector.Config(url)
  }
}

@Singleton
class ImportControlInboundSoapConnectorConfigProvider @Inject() (val configuration: Configuration)
    extends ServicesConfig(configuration)
    with Provider[ImportControlInboundSoapConnector.Config] {

  override def get(): ImportControlInboundSoapConnector.Config = {
    val url                   = baseUrl("import-control-inbound-soap")
    val testForwardMessageUrl = getString("testForwardMessageUrl")
    ImportControlInboundSoapConnector.Config(url, testForwardMessageUrl)
  }
}

@Singleton
class SdesConnectorConfigProvider @Inject() (val configuration: Configuration)
    extends ServicesConfig(configuration)
    with Provider[SdesConnector.Config] {

  override def get(): SdesConnector.Config = {
    val url        = baseUrl("secure-data-exchange-proxy")
    val uploadPath = s"$url/upload-attachment"
    val ics2       = Ics2(
      srn = getConfString("secure-data-exchange-proxy.ics2.srn", "ICS2-SRN-MISSING"),
      informationType = getConfString("secure-data-exchange-proxy.ics2.informationType", "ICS2-INFO-TYPE-MISSING"),
      encodeSdesReference = getConfBool("secure-data-exchange-proxy.ics2.encodeSdesReference", defBool = false)
    )
    val crdl       = Crdl(
      srn = getConfString("secure-data-exchange-proxy.crdl.srn", "CRDL-SRN-MISSING"),
      informationType = getConfString("secure-data-exchange-proxy.crdl.informationType", "CRDL-INFO-TYPE-MISSING")
    )
    SdesConnector.Config(url, uploadPath, ics2, crdl)
  }
}

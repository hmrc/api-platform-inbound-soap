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

import javax.inject.{Inject, Singleton}

import play.api.Configuration

@Singleton
class AppConfig @Inject() (config: Configuration) {

  val appName: String               = config.get[String]("appName")
  val hmacSecret: String            = config.get[String]("hmacSecret")
  val ics2SdesSrn: String           = config.get[String]("ics2SdesSrn")
  val ics2SdesInfoType: String      = config.get[String]("ics2SdesInfoType")
  val crdlSdesSrn: String           = config.get[String]("crdlSdesSrn")
  val crdlSdesInfoType: String      = config.get[String]("crdlSdesInfoType")
  val forwardMessageUrl: String     = config.get[String]("forwardMessageUrl")
  // TODO put something resembling proper data in here
  val sdesUrl: String               = config.get[String]("sdesUrl")
  val passThroughProtocol: String   = config.get[String]("passThroughProtocol")
  val passThroughHost: String       = config.get[String]("passThroughHost")
  val passThroughPort: Int          = config.get[Int]("passThroughPort")
  val testForwardMessageUrl: String = config.get[String]("testForwardMessageUrl")
  val proxyRequired: Boolean        = config.get[Boolean]("http-verbs.proxy.enabled")
}

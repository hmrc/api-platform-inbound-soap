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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers.actionBuilders

import _root_.uk.gov.hmrc.http.HttpErrorFunctions
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.JWTVerifier
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, Result}

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VerifyJwtTokenAction @Inject()(jwtVerifier: JWTVerifier)(implicit ec: ExecutionContext)
    extends ActionFilter[Request] with HttpErrorFunctions with Logging {
  override def executionContext: ExecutionContext = ec

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    val jwtToken: Option[String] = request.headers.get("Authorization")
    verifyJwtToken(jwtToken) match {
      case Right(_) => successful(Some(Ok))
      case Left(e) => {
        logger.warn(s"""JWT token verification failed. ${e.getMessage}""")
        successful(Some(Forbidden))
      }
    }
  }

  private def verifyJwtToken(jwtToken: Option[String]): Either[JWTVerificationException, Unit] = {
    def extractJwtTokenFromHeaderValue(authHeader: Option[String]): String = {
       authHeader.map(headerValue => headerValue.split("\\s")).filter(parts => parts.length == 2).map(parts => parts(1)).getOrElse("")
    }

    try {
      jwtVerifier.verify(extractJwtTokenFromHeaderValue(jwtToken))
      Right(())
    } catch {
      case e: JWTVerificationException => Left(e)
    }
  }
}

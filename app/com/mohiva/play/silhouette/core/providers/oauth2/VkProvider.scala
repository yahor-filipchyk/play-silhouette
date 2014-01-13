/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.core.providers.oauth2

import play.api.libs.json.JsObject
import play.api.mvc.RequestHeader
import play.api.i18n.Lang
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{HTTPLayer, CacheLayer}
import com.mohiva.play.silhouette.core.providers.{OAuth2Identity, OAuth2Info, OAuth2Settings, OAuth2Provider}
import VkProvider._
import OAuth2Provider._

/**
 * A Vk OAuth 2 provider.
 *
 * @param settings The provider settings.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param identityBuilder The identity builder implementation.
 */
class VkProvider[I <: Identity](
    settings: OAuth2Settings,
    cacheLayer: CacheLayer,
    httpLayer: HTTPLayer,
    identityBuilder: IdentityBuilder[VKIdentity, I])
  extends OAuth2Provider[I](settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Vk

  /**
   * Builds the identity.
   *
   * @param authInfo The auth info received from the provider.
   * @param request The request header.
   * @param lang The current lang.
   * @return The identity.
   */
  def buildIdentity(authInfo: OAuth2Info)(implicit request: RequestHeader, lang: Lang): Future[I] = {
    httpLayer.url(API.format(authInfo.accessToken)).get().map { response =>
      val json = response.json
      (json \ Error).asOpt[JsObject] match {
        case Some(error) =>
          val message = (error \ ErrorMessage).as[String]
          val errorCode = (error \ ErrorCode).as[Int]

          throw new AuthenticationException(SpecifiedProfileError.format(errorCode, message))
        case _ =>
          val me = (json \ Response).apply(0)
          val userId = (me \ ID).as[Long]
          val firstName = (me \ FirstName).as[String]
          val lastName = (me \ LastName).as[String]
          val avatarURL = (me \ Photo).asOpt[String]

          identityBuilder(VKIdentity(
            identityID = IdentityID(userId.toString, id),
            firstName = firstName,
            lastName = lastName,
            avatarURL = avatarURL,
            authMethod = authMethod,
            authInfo = authInfo))
      }
    }.recover { case e => throw new AuthenticationException(UnspecifiedProfileError.format(id), e) }
  }
}

/**
 * The companion object.
 */
object VkProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Facebook constants.
   */
  val Vk = "vk"
  val API = "https://api.vk.com/method/getProfiles?fields=uid,first_name,last_name,photo&access_token=%s"
  val Response = "response"
  val ID = "uid"
  val FirstName = "first_name"
  val LastName = "last_name"
  val Photo = "photo"
  val ErrorCode = "error_code"
  val ErrorMessage = "error_msg"
}

/**
 * The VK identity.
 */
case class VKIdentity(
  identityID: IdentityID,
  firstName: String,
  lastName: String,
  avatarURL: Option[String],
  authMethod: AuthenticationMethod,
  authInfo: OAuth2Info) extends OAuth2Identity

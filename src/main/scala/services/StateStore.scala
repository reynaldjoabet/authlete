package services
import cats._
import cats.data._
import cats.effect._
import cats.effect.std.Semaphore
import cats.implicits._
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.nimbusds.jose.{Algorithm, EncryptionMethod, JWEAlgorithm, JWSAlgorithm}
import com.nimbusds.jose.jwk._
import com.nimbusds.jose.jwk.source._
import com.nimbusds.jose.proc._
import com.nimbusds.jwt._
import com.nimbusds.jwt.proc._
import com.nimbusds.oauth2.sdk.id.State      // CSRF state parameter
import com.nimbusds.oauth2.sdk.id.ClientID   // client_id
import com.nimbusds.oauth2.sdk.id.Issuer     // iss claim
import com.nimbusds.oauth2.sdk.auth.Secret   // client_secret
import com.nimbusds.openid.connect.sdk.Nonce // nonce for replay protection
import com.nimbusds.oauth2.sdk._
import com.nimbusds.oauth2.sdk.auth._
import com.nimbusds.oauth2.sdk.id._
import com.nimbusds.openid.connect.sdk.nativesso.DeviceSSOScopeValue
import com.nimbusds.openid.connect.sdk.nativesso.DeviceSecretToken
import com.nimbusds.oauth2.sdk.token._
import com.nimbusds.openid.connect.sdk._
import com.nimbusds.openid.connect.sdk.claims._
import com.nimbusds.openid.connect.sdk.claims.StateHash
import com.nimbusds.openid.connect.sdk.op._
import com.nimbusds.openid.connect.sdk.validators._

import com.nimbusds.openid.connect.sdk.validators.AbstractJWTValidator
import com.nimbusds.openid.connect.sdk.validators.AccessTokenValidator
import com.nimbusds.openid.connect.sdk.validators.AuthorizationCodeValidator
import com.nimbusds.openid.connect.sdk.validators.IDTokenClaimsVerifier
import com.nimbusds.openid.connect.sdk.validators.LogoutTokenClaimsVerifier
import com.nimbusds.openid.connect.sdk.validators.LogoutTokenValidator
import com.nimbusds.openid.connect.sdk.validators.StateValidator

import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.typelevel.log4cats.Logger
import java.net.URI
import java.time.{Duration => JDuration, Instant}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

case class AuthState(
      clientID: ClientID,
      redirectURI: URI,
      scope: Scope,
      nonce: Option[String],
      pkceVerifier: Option[String],
      createdAt: Instant
  )

trait StateStore[F[_]] {
    def save(state: State, authState: AuthState): F[Unit]
    def retrieve(state: State): F[Option[AuthState]]
    def delete(state: State): F[Unit]
  }

  object StateStore {
    // In-memory store (use Redis/DB in production)
    def inMemory[F[_]: Sync]: F[StateStore[F]] = 
      Ref.of[F, Map[String, AuthState]](Map.empty).map { ref =>
        new StateStore[F] {
          def save(state: State, authState: AuthState): F[Unit] =
            ref.update(_ + (state.getValue -> authState))

          def retrieve(state: State): F[Option[AuthState]] =
            ref.get.map(_.get(state.getValue))

          def delete(state: State): F[Unit] =
            ref.update(_ - state.getValue)
        }
      }
  }


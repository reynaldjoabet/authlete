package domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

final case class DpopClaim(
    htu: String,
    htm: String,
    exp: Long,
    jti: String,
    iat: Long,
    nonce: Option[String],
    ath: Option[String] // should be non optional for a resource server
)

object DpopClaim {

  /// Unique identifier for the DPoP proof JWT. The value MUST be
  /// assigned such that there is a negligible probability that the
  /// same value will be assigned to any other DPoP proof used in the
  /// same context during the time window of validity. Such uniqueness
  /// can be accomplished by encoding (base64url or any other suitable
  /// encoding) at least 96 bits of pseudorandom data or by using a
  /// version 4 Universally Unique Identifier (UUID) string according
  /// to [RFC4122]. The jti can be used by the server for replay
  /// detection and prevention; see Section 11.1.
  val jti: String = "hello"
  /// The value of the HTTP method (Section 9.1 of [RFC9110]) of the
  /// request to which the JWT is attached.
  val htm: String = ""
  /// The HTTP target URI (Section 7.1 of [RFC9110]) of the request
  /// to which the JWT is attached, without query and fragment parts.
  val htu: String = "hello"
  /// Creation timestamp of the JWT (Section 4.1.6 of [RFC7519]).
  val iat: Int = 12
  // The 'ath' claim does not apply to Rauthy, only used by resource servers.
  // It is just here for completeness.
  //
  // Hash of the access token. The value MUST be the result of a
  // base64url encoding (as defined in Section 2 of [RFC7515])
  // the SHA-256 [SHS] hash of the ASCII encoding of the associated
  // access token's value.
  //
  // MUST be valid when used in conjunction with an access token
  /// A recent nonce provided via the DPoP-Nonce HTTP header.

  val nonce: Option[String] = None

}

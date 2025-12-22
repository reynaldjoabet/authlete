package domain.models

/** Device Secret.
  *
  * <p> This class represents the concept of a "<a href=
  * "https://openid.net/specs/openid-connect-native-sso-1_0.html#name-device-secret"
  * >Device Secret</a>" introduced by the "<a href=
  * "https://openid.net/specs/openid-connect-native-sso-1_0.html">OpenID Connect
  * Native SSO for Mobile Apps 1.0</a>" specification ("Native SSO"). </p>
  *
  * <p> The following is an excerpt from the specification describing the
  * concept: </p>
  *
  * <blockquote> <p><i> The device secret contains relevant data to the device
  * and the current users authenticated with the device. The device secret is
  * completely opaque to the client and as such the AS MUST adequately protect
  * the value such as using a JWE if the AS is not maintaining state on the
  * backend. </i></p> </blockquote>
  *
  * @see
  *   <a
  *   href="https://openid.net/specs/openid-connect-native-sso-1_0.html#name-device-secret"
  *   >OpenID Connect Native SSO for Mobile Apps 1.0, Section 3.2. Device
  *   Secret</a>
  */

final case class DeviceSecret(
    /** The value of this device secret. Get the value of this device secret.
      * This corresponds to the value of the {@code device_secret} parameter in
      * token responses.
      */
    value: String,
    /** The value of the hash of this device secret. Get the value of the hash
      * of this device secret. This corresponds to the value of the
      * {@code ds_hash} claim in the Native SSO-compliant ID token.
      */
    hash: String,
    /** The identifier of the user's authentication session associated with this
      * device secret. * Set the identifier of the user's authentication session
      * associated with this device secret. This corresponds to the {@code sid}
      * claim in the Native SSO-compliant ID token.
      */

    sessionId: String,
    /** The identifier of the device associated with this device secret.
      */
    deviceId: String
)

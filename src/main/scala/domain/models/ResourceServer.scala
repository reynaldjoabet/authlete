package domain.models

import java.net.URI
import java.time.Instant
import authlete.models.{JweAlg, JweEnc, JwsAlg}

/** Immutable Scala model for ResourceServerEntity (Scala-friendly). */
final case class ResourceServer(
    id: String,
    secret: Option[String] = None,
    uri: Option[URI] = None,
    introspectionSignAlg: Option[JwsAlg] = None,
    introspectionEncryptionAlg: Option[JweAlg] = None,
    introspectionEncryptionEnc: Option[JweEnc] = None,
    sharedKeyForIntrospectionResponseSign: Option[String] = None,
    sharedKeyForIntrospectionResponseEncryption: Option[String] = None,
    publicKeyForIntrospectionResponseEncryption: Option[String] = None
)

package http.security

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.ThumbprintUtils.compute

object TrustChainValidator {
  // def validate(jwtHeader: JWK) = {
  // val thumbprint = compute(jwtHeader)
  // thumbprint == "thumbprint"
  // }
}

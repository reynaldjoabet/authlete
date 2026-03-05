package domain.models

import pdi.jwt.JwtClaim

final case class Principal(sub: String, scopes: Set[String], rawClaim: JwtClaim)

case class Principal2(
    sub: String,
    scopes: Set[String],
    roles: Set[String],
    claims: com.nimbusds.jwt.JWTClaimsSet
)

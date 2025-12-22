package api.requests

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

final case class AuthorizationDecisionParam(
    ticket: String,
    claimNames: List[String],
    claimLocales: List[String],
    idTokenClaims: Option[String],
    requestedClaimsForTx: List[String],
    requestedVerifiedClaimsForTx: List[List[String]],
    oldIdaFormatUsed: Boolean
) derives ConfiguredJsonValueCodec

object AuthorizationDecisionParam {

  // given Transformer[AuthorizationDecisionRequest, authlete.dto.request.AuthorizationDecisionRequest] =

}

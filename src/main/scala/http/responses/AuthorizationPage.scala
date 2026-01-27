package http.responses

import authlete.models.{Pair, Scope}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import domain.models.User

/**
  * Model class to hold data which are referred to in an authorization page.
  */

final case class AuthorizationPage(
    /**
      * The name of the service.
      */
    serviceName: String,

    /**
      * The name of the client application.
      */
    clientName: String,

    /**
      * The description of the client application.
      */
    description: Option[String],

    /**
      * The URL of the logo image of the client application.
      */
    logoUri: Option[String],

    /**
      * The URL of the homepage of the client application.
      */
    clientUri: Option[String],

    /**
      * The URL of the policy page of the client application.
      */
    policyUri: Option[String],

    /**
      * The URL of "Terms of Service" page of the client application.
      */
    tosUri: Option[String],

    /**
      * Scopes requested by the authorization request.
      */
    scopes: List[Scope],

    /**
      * The login ID that should be used as the initial value for the login ID field in the
      * authorization page.
      */
    loginId: String,

    /**
      * This variable holds {@code "readonly"} when the initial value of the login ID should not be
      * changed.
      */
    loginIdReadOnly: Option[String],

    /**
      * Currently logged in user, could be null if no user is logged in.
      */
    user: User,

    /**
      * The content of the {@code authorization_details} request parameter in JSON format. See
      * "OAuth 2.0 Rich Authorization Requests".
      */
    authorizationDetails: String,

    /**
      * The value of the {@code purpose} request parameter.
      */
    purpose: Option[String],

    /**
      * Verified claims requested for the ID token.
      */
    verifiedClaimsForIdToken: List[Pair],

    /**
      * Flag indicating whether the authorization request requests all possible verified claims for
      * the ID token.
      */
    allVerifiedClaimsForIdTokenRequested: Option[Boolean],

    /**
      * Verified claims requested for the userinfo.
      */
    verifiedClaimsForUserInfo: List[Pair],

    /**
      * Flag indicating whether the authorization request requests all possible verified claims for
      * the userinfo.
      */
    allVerifiedClaimsForUserInfoRequested: Option[Boolean],

    /**
      * Flag indicating whether behaviors for Identity Assurance are required.
      */
    identityAssuranceRequired: Option[Boolean],

    /**
      * Flag indicating whether this class assumes that the old format of {@code "verified_claims"}
      * is used. "Old" here means the 2nd Implementer's Draft of OpenID Connect for Identity
      * Assurance 1.0.
      */
    oldIdaFormatUsed: Option[Boolean],

    /**
      * Claims that the client application requests to be embedded in the ID token.
      */
    claimsForIdToken: List[String],

    /**
      * Claims that the client application requests to be embedded in userinfo responses.
      */
    claimsForUserInfo: List[String]
) derives ConfiguredJsonValueCodec

package domain.models

final case class Consent(
    consentId: Option[String] = None,
    permissions: List[String] = Nil,
    status: Option[String] = None,
    creationDateTime: Option[String] = None,
    expirationDateTime: Option[String] = None,
    statusUpdateDateTime: Option[String] = None,
    clientId: Option[Long] = None,
    refreshToken: Option[String] = None
)

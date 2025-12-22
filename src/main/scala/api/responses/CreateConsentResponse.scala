package api.responses

import domain.models.{Links, Meta}

/** ResponseConsent.
  *
  * @see
  *   <a
  *   href="https://openbanking-brasil.github.io/areadesenvolvedor/#tocS_ResponseConsent"
  *   >ResponseConsent</a>
  */

final case class CreateConsentResponse(
    consentId: Option[String] = None,
    creationDateTime: Option[String] = None,
    status: Option[String] = None,
    statusUpdateDateTime: Option[String] = None,
    permissions: List[String] = Nil,
    expirationDateTime: Option[String] = None,
    transactionFromDateTime: Option[String] = None,
    transactionToDateTime: Option[String] = None,
    links: Option[Links] = None,
    meta: Option[Meta] = None
)

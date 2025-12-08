package models.requests

import models.{BusinessEntity, LoggedUser}
final case class CreateConsent(
    loggedUser: Option[LoggedUser] = None,
    businessEntity: Option[BusinessEntity] = None,
    permissions: List[String] = Nil,
    expirationDateTime: Option[String] = None,
    transactionFromDateTime: Option[String] = None,
    transactionToDateTime: Option[String] = None
)

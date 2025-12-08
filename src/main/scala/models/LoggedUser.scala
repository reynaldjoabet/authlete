package models

import models.Document

/** LoggedUser
  *
  * @see
  *   <a
  *   href="https://openbanking-brasil.github.io/areadesenvolvedor/#tocS_LoggedUser"
  *   >LoggedUser</a>
  */
final case class LoggedUser(document: Document)

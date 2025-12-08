package models.responses
import models.{Account, Links, Meta}

/** ResponseAccountList
  *
  * @see
  *   <a
  *   href="https://openbanking-brasil.github.io/areadesenvolvedor/#tocS_ResponseAccountList"
  *   >ResponseAccountList</a>
  */
final case class ResponseAccountList(
    data: List[Account] = Nil,
    links: Option[Links] = None,
    meta: Option[Meta] = None
)

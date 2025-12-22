package api.responses

import domain.models.Meta

/** ResponseError
  *
  * @see
  *   <a
  *   href="https://openbanking-brasil.github.io/areadesenvolvedor/#tocS_ResponseError"
  *   >ResponseError</a>
  */
final case class ResponseError(
    errrors: List[String] = Nil,
    meta: Option[Meta] = None
)

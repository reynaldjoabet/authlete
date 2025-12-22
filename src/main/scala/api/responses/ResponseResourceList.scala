package api.responses

import domain.models.{Links, Meta, Resource}

/** ResponseResourceList.
  *
  * @see
  *   <a
  *   href="https://openbanking-brasil.github.io/areadesenvolvedor/#tocS_ResponseResourceList"
  *   >ResponseResourceList</a>
  */
final case class ResponseResourceList(
    data: List[Resource] = Nil,
    links: Option[Links] = None,
    meta: Option[Meta] = None
)

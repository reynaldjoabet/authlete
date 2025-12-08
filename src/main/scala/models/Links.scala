package models

/** Links
  *
  * @see
  *   <a
  *   href="https://openbanking-brasil.github.io/areadesenvolvedor/#tocS_Links"
  *   >Links</a>
  */
final case class Links(
    self: Option[String] = None,
    first: Option[String] = None,
    prev: Option[String] = None,
    next: Option[String] = None,
    last: Option[String] = None
)

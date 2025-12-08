package models

/** Meta
  *
  * @see
  *   <a
  *   href="https://openbanking-brasil.github.io/areadesenvolvedor/#tocS_Meta"
  *   >Meta</a>
  */
final case class Meta(
    totalRecords: Int,
    totalPages: Int,
    requestDateTime: Option[String] = None
)

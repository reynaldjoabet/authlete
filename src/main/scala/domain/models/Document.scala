package domain.models

final case class Document(
    identification: Option[String] = None,
    rel: Option[String] = None
)

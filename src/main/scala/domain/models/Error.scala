package domain.models

final case class Error(
    code: String,
    title: String,
    detail: String
)

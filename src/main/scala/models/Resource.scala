package models

final case class Resource(
    resourceId: Option[String] = None,
    `type`: Option[String] = None,
    status: Option[String] = None
)

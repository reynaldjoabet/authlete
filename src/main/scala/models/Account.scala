package models

final case class Account(
    brandName: Option[String] = None,
    companyCnpj: Option[String] = None,
    `type`: Option[String] = None,
    compeCode: Option[String] = None,
    branchCode: Option[String] = None,
    number: Option[String] = None,
    checkDigit: Option[String] = None,
    accountId: Option[String] = None
)

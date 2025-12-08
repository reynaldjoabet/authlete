package models

import java.time.Instant

final case class User(
    subject: String,
    loginId: Option[String] = None,
    password: Option[String] = None,
    name: Option[String] = None,
    email: Option[String] = None,
    // address: Option[Address] = None,
    phoneNumber: Option[String] = None,
    code: Option[String] = None,
    // Standard claims
    phoneNumberVerified: Option[Boolean] = None,
    emailVerified: Option[Boolean] = None,
    givenName: Option[String] = None,
    familyName: Option[String] = None,
    middleName: Option[String] = None,
    nickName: Option[String] = None,
    profile: Option[String] = None,
    picture: Option[String] = None,
    website: Option[String] = None,
    gender: Option[String] = None,
    zoneinfo: Option[String] = None,
    locale: Option[String] = None,
    preferredUsername: Option[String] = None,
    birthdate: Option[String] = None,
    updatedAt: Option[Instant] = None
)

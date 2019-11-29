package se.su.dsv.oauth

import argonaut._, Argonaut._

final case class Payload(
  principal: String,
  displayName: Option[String],
  mail: Option[String],
  entitlements: Entitlements
)
object Payload {
  implicit val payloadEncodeJson: EncodeJson[Payload] =
    EncodeJson { case Payload(principal, displayName, mail, entitlements) =>
      // We send principal twice to work with both Shibboleth and JWT standard names
      ("principal", jString(principal)) ->:
      ("sub", jString(principal)) ->:
      ("name", displayName.fold(jNull)(jString)) ->:
      ("mail", mail.fold(jNull)(jString)) ->:
      ("entitlements", jArray(entitlements.values.map(jString))) ->:
        jEmptyObject
    }
}

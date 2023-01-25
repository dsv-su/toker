package se.su.dsv.oauth

import io.circe._

final case class Payload(
  principal: String,
  displayName: Option[String],
  mail: Option[String],
  entitlements: Entitlements
)
object Payload {
  implicit val payloadEncodeJson: Encoder[Payload] = {
    case Payload(principal, displayName, mail, entitlements) =>
      Json.obj(
        // We send principal twice to work with both Shibboleth and JWT standard names
        ("principal", Json.fromString(principal)),
        ("sub", Json.fromString(principal)),
        ("name", displayName.fold(Json.Null)(Json.fromString)),
        ("mail", mail.fold(Json.Null)(Json.fromString)),
        ("entitlements", Json.arr(entitlements.values.map(Json.fromString): _*))
      )
  }
}

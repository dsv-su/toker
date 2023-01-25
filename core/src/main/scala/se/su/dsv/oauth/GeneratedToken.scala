package se.su.dsv.oauth

import java.time.Duration

import io.circe._

import json._

final case class Token(token: String)
object Token {
  sealed trait Type
  object Type {
    case object Bearer extends Type

    implicit val encodeJson: Encoder[Type] =
      Encoder {
        case Bearer => Json.fromString("bearer")
      }
  }

  implicit val tokenEncodeJson: Encoder[Token] =
    Encoder[String].contramap(_.token)
}

final case class GeneratedToken(token: Token, duration: Duration)

final case class TokenResponse(token: Token, typ: Token.Type, expiresIn: Option[Duration] = None)
object TokenResponse {
  implicit val encodeJson: Encoder[TokenResponse] =
    Encoder.forProduct3("access_token", "token_type", "expires_in")(r => (r.token, r.typ, r.expiresIn))
}

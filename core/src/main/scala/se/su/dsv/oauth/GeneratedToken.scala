package se.su.dsv.oauth

import java.time.Duration

import argonaut.Argonaut._
import argonaut._

import json._

final case class Token(token: String)
object Token {
  sealed trait Type
  object Type {
    case object Bearer extends Type

    implicit val encodeJson: EncodeJson[Type] =
      EncodeJson {
        case Bearer => jString("bearer")
      }
  }

  implicit val tokenEncodeJson: EncodeJson[Token] =
    EncodeJson.of[String].contramap(_.token)
}

final case class GeneratedToken(token: Token, duration: Duration)

final case class TokenResponse(token: Token, typ: Token.Type, expiresIn: Option[Duration] = None)
object TokenResponse {
  implicit val encodeJson: EncodeJson[TokenResponse] =
    jencode3L((r: TokenResponse) => (r.token, r.typ, r.expiresIn))("access_token", "token_type", "expires_in")
}

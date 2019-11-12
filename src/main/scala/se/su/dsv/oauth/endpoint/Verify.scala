package se.su.dsv.oauth.endpoint

import argonaut._
import Argonaut._
import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all._
import org.http4s._
import org.http4s.argonaut._
import org.http4s.dsl._
import se.su.dsv.oauth.{Payload, Token}

class Verify[F[_]]
(
  lookupToken: Token => OptionT[F, Payload]
)(implicit S: Sync[F]) extends Http4sDsl[F]
{

  def service: HttpService[F] = HttpService[F] {
    case request @ POST -> Root =>
      val prg = for {
        uuid <- OptionT.liftF(request.as[String])
        payload <- lookupToken(Token(uuid))
      } yield payload

      prg.value.flatMap {
        case Some(payload) => Ok(payload.asJson)
        case None => Forbidden()
      }
  }
}

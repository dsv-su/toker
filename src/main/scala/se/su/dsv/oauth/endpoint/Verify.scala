package se.su.dsv.oauth.endpoint

import argonaut._, Argonaut._
import org.http4s._
import org.http4s.argonaut._
import org.http4s.dsl._
import se.su.dsv.oauth.{Payload, Token}

import scalaz.OptionT
import scalaz.concurrent.Task
import scalaz.syntax.monad._

class Verify
(
  lookupToken: Token => OptionT[Task, Payload]
)
{

  def service: HttpService = HttpService {
    case request @ POST -> Root =>
      val prg = for {
        uuid <- request.as[String].liftM[OptionT]
        payload <- lookupToken(Token(uuid))
      } yield payload

      prg.run.flatMap {
        case Some(payload) => Ok(payload.asJson)
        case None => Forbidden()
      }
  }
}

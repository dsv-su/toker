package se.su.dsv.oauth.endpoint

import cats.data.OptionT
import cats.effect.Concurrent
import cats.syntax.all._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.typelevel.ci._
import se.su.dsv.oauth.{Payload, Token}

class Verify[F[_]]
(
  lookupToken: Token => OptionT[F, Payload]
)(implicit S: Concurrent[F]) extends Http4sDsl[F]
{

  def service: HttpRoutes[F] = HttpRoutes.of[F] {
    case request @ GET -> Root =>
      request.headers.get[Authorization] match {
        case Some(Authorization(Credentials.Token(scheme, token))) if scheme == ci"Bearer" =>
          lookupToken(Token(token)).value.flatMap {
            case Some(payload) => Ok(payload.asJson)
            case None => Forbidden()
          }
        case _ => Forbidden()
      }
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

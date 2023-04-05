package se.su.dsv.oauth.endpoint

import cats.effect.Concurrent
import cats.syntax.all.*
import io.circe.{Encoder, Json}
import io.circe.syntax.*
import org.http4s.{BasicCredentials, HttpRoutes, MalformedMessageBodyFailure, UrlForm}
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import se.su.dsv.oauth.Token
import se.su.dsv.oauth.administration.ResourceServer

import java.time.Instant

class Introspect[F[_]]
  ( lookupToken: Token => F[Introspection]
  , lookupResourceServerSecret: String => F[Option[String]]
  )
(using F: Concurrent[F])
  extends Http4sDsl[F]
{
  def service: HttpRoutes[F] = HttpRoutes.of {
    case request@POST -> Root =>
      for {
        formData <- request.as[UrlForm]
        _ <- validateCredentials(request.headers.get[Authorization])
        opaqueString <- F.fromOption(formData.getFirst("token"), MalformedMessageBodyFailure("No token provided"))
        introspection <- lookupToken(Token(opaqueString))
        response <- Ok(introspection.asJson)
      } yield response
  }

  private def validateCredentials(authorization: Option[Authorization]): F[Unit] = {
    authorization match {
      case Some(Authorization(BasicCredentials(resourceServerId, providedSecret))) =>
        for {
          maybeSecret <- lookupResourceServerSecret(resourceServerId)
          secret <- F.fromOption(maybeSecret, InvalidCredentials)
          _ <- F.raiseUnless(secret == providedSecret)(InvalidCredentials)
        } yield ()
      case _ =>
        F.raiseError(InvalidCredentials)
    }
  }
}

sealed trait Introspection

object Introspection {
  case class Active(subject: String, expiration: Instant) extends Introspection

  case object Inactive extends Introspection

  implicit def jsonEncoder: Encoder[Introspection] =
    Encoder.instance {
      case Active(subject, expiration) =>
        Json.obj(
          "active" -> Json.True,
          "sub" -> subject.asJson,
          "exp" -> expiration.getEpochSecond.asJson)
      case Inactive => Json.obj("active" -> Json.False)
    }
}

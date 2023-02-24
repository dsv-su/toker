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

import java.time.Instant

class Introspect[F[_]]
(lookupToken: Token => F[Introspection])
(using F: Concurrent[F])
  extends Http4sDsl[F]
{
  def service: HttpRoutes[F] = HttpRoutes.of {
    case request@POST -> Root =>
      for {
        formData <- request.as[UrlForm]
        _ <- F.fromOption(request.headers.get[Authorization], InvalidCredentials)
        // todo: validate credentials
        opaqueString <- F.fromOption(formData.getFirst("token"), MalformedMessageBodyFailure("No token provided"))
        introspection <- lookupToken(Token(opaqueString))
        response <- Ok(introspection.asJson)
      } yield response
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

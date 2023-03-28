package se.su.dsv.oauth.endpoint

import cats.data.{EitherT, OptionT}
import cats.data.OptionT.{none, some}
import cats.effect.Concurrent
import cats.syntax.all.*
import org.http4s.{Response, Uri}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import se.su.dsv.oauth.{AuthorizationRequest, Client, Code, GeneratedToken, Payload, ResponseType}

sealed trait AuthorizationRequestError extends RuntimeException
case class NoSuchClient(str: String) extends AuthorizationRequestError
case class InvalidScopes(requested: Set[String], allowed: Set[String]) extends AuthorizationRequestError
case class InvalidRedirectUri(requested: Option[Uri], allowed: Uri) extends AuthorizationRequestError

class AbstractAuthorize[F[_]]
(lookupClient: String => OptionT[F, Client],
 generateToken: Payload => F[GeneratedToken],
 generateCode: (String, Option[Uri], Payload) => F[Code])
(using F: Concurrent[F])
  extends Http4sDsl[F] {

  def authorize(authorizationRequest: AuthorizationRequest, payload: Payload): F[Response[F]] = {
    val response = for {
      client <- lookupClient(authorizationRequest.clientId)
        .getOrRaise(NoSuchClient(authorizationRequest.clientId))
      _ <- F.raiseUnless(authorizationRequest.scopes.forall(client.allowedScopes))(
        InvalidScopes(
          requested = authorizationRequest.scopes,
          allowed = client.allowedScopes
        ))
      redirectUri <-
        if authorizationRequest.redirectUri.forall(_ == client.redirectUri)
        then F.pure(client.redirectUri)
        else F.raiseError(InvalidRedirectUri(
          requested = authorizationRequest.redirectUri,
          allowed = client.redirectUri
        ))
      callbackUri <- authorizationRequest.responseType match {
        case ResponseType.Code =>
          for {
            code <- generateCode(authorizationRequest.clientId, authorizationRequest.redirectUri, payload)
          } yield redirectUri +*? code +?? ("state", authorizationRequest.state)
        case ResponseType.Token =>
          generateToken(payload) map { token =>
            val parameters = Map(
              "access_token" -> token.token.token,
              "token_type" -> "Bearer",
              "expires_in" -> String.valueOf(token.duration.getSeconds),
              "state" -> authorizationRequest.state.getOrElse("")
            )
            redirectUri.copy(
              fragment = Some(parameters.foldLeft("") { case (s, (key, value)) => s"$s&$key=$value" })
            )
          }
      }
    } yield SeeOther(Location(callbackUri))
    response.flatten.recoverWith {
      case error: AuthorizationRequestError => Forbidden(error.toString)
    }
  }
}

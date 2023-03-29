package se.su.dsv.oauth.endpoint

import cats.data.{EitherT, OptionT}
import cats.data.OptionT.{none, some}
import cats.effect.Concurrent
import cats.syntax.all.*
import org.http4s.{Response, Uri}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import se.su.dsv.oauth.{AuthorizationRequest, Client, Code, GeneratedToken, Payload, ProofKey, ResponseType}

sealed trait AuthorizationRequestError extends RuntimeException
case class NoSuchClient(str: String) extends AuthorizationRequestError
case class InvalidScopes(requested: Set[String], allowed: Set[String]) extends AuthorizationRequestError
case class InvalidRedirectUri(requested: Option[Uri], allowed: Uri) extends AuthorizationRequestError
case object InvalidRequest extends AuthorizationRequestError

class AbstractAuthorize[F[_]]
(lookupClient: String => OptionT[F, Client],
 generateCode: (String, Option[Uri], Payload, Option[ProofKey]) => F[Code])
(using F: Concurrent[F])
  extends Http4sDsl[F] {

  def authorize(authorizationRequest: AuthorizationRequest, payload: Payload): F[Response[F]] = {
    val response = for {
      client <- lookupClient(authorizationRequest.clientId)
        .getOrRaise(NoSuchClient(authorizationRequest.clientId))
      _ <- F.raiseWhen(client.isPublic && authorizationRequest.proofKey.isEmpty)(InvalidRequest)
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
            code <- generateCode(authorizationRequest.clientId, authorizationRequest.redirectUri, payload, authorizationRequest.proofKey)
          } yield redirectUri +*? code +?? ("state", authorizationRequest.state)
      }
    } yield SeeOther(Location(callbackUri))
    response.flatten.recoverWith {
      case error: AuthorizationRequestError => Forbidden(error.toString)
    }
  }
}

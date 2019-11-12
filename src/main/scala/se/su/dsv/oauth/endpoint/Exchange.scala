package se.su.dsv.oauth.endpoint

import argonaut._
import Argonaut._
import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import cats.syntax.all._
import org.http4s._
import org.http4s.argonaut._
import org.http4s.dsl._
import org.http4s.headers.Authorization
import se.su.dsv.oauth.AccessTokenRequest.ErrorResponse
import se.su.dsv.oauth._

class Exchange[F[_]]
(
  lookupClient: String => OptionT[F, Client],
  lookupCode: (String, String) => OptionT[F, Code],
  generateToken: Payload => F[GeneratedToken]
)(implicit S: Sync[F]) extends Http4sDsl[F]
{
  private def right[A](a: A): EitherT[F, ErrorResponse, A] =
    EitherT.right(Sync[F].pure(a))
  private def left[A](e: ErrorResponse): EitherT[F, ErrorResponse, A] =
    EitherT.left(Sync[F].pure(e))

  private def validateCredentials(providedSecret: String, clientSecret: String): EitherT[F, ErrorResponse, Unit] = {
    if (providedSecret == clientSecret)
      right(())
    else
      left(AccessTokenRequest.invalidClient)
  }

  private def getCredentials(request: Request[F]): EitherT[F, ErrorResponse, ClientCredentials] = {
    request.headers.get(Authorization) match {
      case Some(Authorization(BasicCredentials(username, password))) =>
        right(ClientCredentials(username, password))
      case _ =>
        left(AccessTokenRequest.invalidClient)
    }
  }

  private def validateRedirectUri(expected: Option[Uri], requested: Option[Uri]): EitherT[F, ErrorResponse, Unit] = {
    (expected, requested) match {
      case (Some(e), Some(r)) if e == r =>
        right(())
      case (None, None) =>
        right(())
      case _ =>
        left(AccessTokenRequest.invalidGrant)
    }
  }

  def service: HttpRoutes[F] = HttpRoutes.of[F] {
    case request @ POST -> Root =>
      val prg = for {
        accessTokenRequest <- AccessTokenRequest.fromRequest(request)
        credentials <- getCredentials(request)
        client <- lookupClient(credentials.clientId)
          .toRight(AccessTokenRequest.invalidClient)
        _ <- validateCredentials(credentials.secret, client.secret)
        code <- lookupCode(credentials.clientId, accessTokenRequest.code)
          .toRight(AccessTokenRequest.invalidGrant)
        _ <- validateRedirectUri(code.redirectUri, accessTokenRequest.redirectUri)
        token <- EitherT.right[AccessTokenRequest.ErrorResponse](generateToken(code.payload))
      } yield {
        TokenResponse(token.token, Token.Type.Bearer, Some(token.duration))
      }

      prg.value.flatMap {
        case Right(tokenResponse) => Ok(tokenResponse.asJson)
        case Left(error) => BadRequest(error.asJson)
      }
  }
}

private final case class ClientCredentials(clientId: String, secret: String)

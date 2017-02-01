package se.su.dsv.oauth.endpoint

import argonaut._
import Argonaut._
import org.http4s._
import org.http4s.argonaut._
import org.http4s.dsl._
import org.http4s.headers.Authorization
import se.su.dsv.oauth.AccessTokenRequest.ErrorResponse
import se.su.dsv.oauth._

import scalaz.{-\/, EitherT, OptionT, \/-}
import scalaz.concurrent.Task

class Exchange
(
  lookupClient: String => OptionT[Task, Client],
  lookupCode: (String, String) => OptionT[Task, Code],
  generateToken: Payload => Task[GeneratedToken]
)
{
  private def right[A](a: A): EitherT[Task, ErrorResponse, A] =
    EitherT.right(Task.now(a))
  private def left[A](e: ErrorResponse): EitherT[Task, ErrorResponse, A] =
    EitherT.left(Task.now(e))

  private def validateCredentials(providedSecret: String, clientSecret: String): EitherT[Task, ErrorResponse, Unit] = {
    if (providedSecret == clientSecret)
      right(())
    else
      left(AccessTokenRequest.invalidClient)
  }

  private def getCredentials(request: Request): EitherT[Task, ErrorResponse, Credentials] = {
    request.headers.get(Authorization) match {
      case Some(Authorization(BasicCredentials(username, password))) =>
        right(Credentials(username, password))
      case _ =>
        left(AccessTokenRequest.invalidClient)
    }
  }

  private def validateRedirectUri(expected: Option[Uri], requested: Option[Uri]): EitherT[Task, ErrorResponse, Unit] = {
    (expected, requested) match {
      case (Some(e), Some(r)) if e == r =>
        right(())
      case (None, None) =>
        right(())
      case _ =>
        left(AccessTokenRequest.invalidGrant)
    }
  }

  def service: HttpService = HttpService {
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
        token <- EitherT.right(generateToken(code.payload))
      } yield {
        TokenResponse(token.token, Token.Type.Bearer, Some(token.duration))
      }

      prg.run.flatMap {
        case \/-(tokenResponse) => Ok(tokenResponse.asJson)
        case -\/(error) => BadRequest(error.asJson)
      }
  }
}

private final case class Credentials(clientId: String, secret: String)

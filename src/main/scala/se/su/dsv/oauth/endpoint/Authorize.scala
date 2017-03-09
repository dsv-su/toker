package se.su.dsv.oauth.endpoint

import java.time.Duration

import org.http4s._
import org.http4s.dsl._
import se.su.dsv.oauth._

import scalaz.OptionT
import scalaz.OptionT.{none, some}
import scalaz.concurrent.Task

class Authorize
(
  lookupClient: String => OptionT[Task, Client],
  generateToken: Payload => Task[GeneratedToken],
  generateCode: (String, Option[Uri], Payload) => Task[Code]
)
{
  private val NonceValidFor = Duration.ofHours(1)

  def validateRedirectUri(requestedRedirectUri: Option[Uri], configuredRedirectUri: Uri): OptionT[Task, Uri] = {
    val redirectUri = requestedRedirectUri getOrElse configuredRedirectUri
    if (redirectUri == configuredRedirectUri)
      some(redirectUri)
    else
      none
  }

  def validateScopes(requestedScopes: Set[String], allowedScopes: Set[String]): OptionT[Task, Set[String]] = {
    if (requestedScopes.forall(allowedScopes))
      some[Task, Set[String]](requestedScopes).filter(_.nonEmpty)
    else
      none
  }

  def validateNonce(form: UrlForm, request: Request): OptionT[Task, Unit] = {
    val formNonce = form.getFirst("nonce")
    val cookieNonce = request.headers.get(headers.Cookie).flatMap(_.values.collectFirst {
      case Cookie(name, content, _, _, _, _, _, _, _) if name == "nonce" => content
    })
    if (formNonce.exists(s => cookieNonce.contains(s)))
      some(())
    else
      none
  }

  private def toPayload(request: Request): Payload =
    Payload(request.attributes(RemoteUser))

  def service: HttpService = HttpService {
    case request @ GET -> Root =>
      val response = for {
        authorizationRequest <- OptionT(Task.now(AuthorizationRequest.fromRaw(request)))
        client <- lookupClient(authorizationRequest.clientId)
        _ <- validateScopes(authorizationRequest.scopes, client.allowedScopes)
        redirectUri <- validateRedirectUri(authorizationRequest.redirectUri, client.redirectUri)
      } yield authorizationRequest.responseType match {
        case ResponseType.Code =>
          for {
            code <- generateCode(authorizationRequest.clientId, authorizationRequest.redirectUri, toPayload(request))
            callback: Uri = redirectUri +*? code +?? ("state", authorizationRequest.state)
            response <- SeeOther(callback)
          } yield response
        case ResponseType.Token =>
          for {
            token <- generateToken(toPayload(request))
            callback: Uri = redirectUri.copy(
              fragment = Some(s"access_token=${token.token.token}&token_type=Bearer&expires_in=${token.duration.getSeconds}&state=${authorizationRequest.state.getOrElse("")}")
            )
            response <- SeeOther(callback)
          } yield response
      }
      response.run.flatMap(_ getOrElse Forbidden())

    case request @ POST -> Root =>
      val response = for {
        form <- EntityDecoder[UrlForm].decode(request, strict = true).toOption
        authorizationRequest <- OptionT(Task.now(AuthorizationRequest.fromForm(form)))
        client <- lookupClient(authorizationRequest.clientId)
        _ <- validateNonce(form, request)
        _ <- validateScopes(authorizationRequest.scopes, client.allowedScopes)
        redirectUri <- validateRedirectUri(authorizationRequest.redirectUri, client.redirectUri)
      } yield authorizationRequest.responseType match {
        case ResponseType.Code =>
          for {
            code <- generateCode(authorizationRequest.clientId, authorizationRequest.redirectUri, toPayload(request))
            callback: Uri = redirectUri +*? code +?? ("state", authorizationRequest.state)
            response <- SeeOther(callback)
          } yield response
        case ResponseType.Token =>
          for {
            token <- generateToken(toPayload(request))
            callback: Uri = redirectUri.copy(
              fragment = Some(s"access_token=${token.token.token}&token_type=Bearer&expires_in=${token.duration.getSeconds}&state=${authorizationRequest.state.getOrElse("")}")
            )
            response <- SeeOther(callback)
          } yield response
      }
      response.run.flatMap(_ getOrElse Forbidden())
  }
}

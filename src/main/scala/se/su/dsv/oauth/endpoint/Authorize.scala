package se.su.dsv.oauth.endpoint

import cats.data.OptionT
import cats.data.OptionT.{none, some}
import cats.effect.Sync
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.{Cookie, Location}
import se.su.dsv.oauth._

class Authorize[F[_]]
(
  lookupClient: String => OptionT[F, Client],
  generateToken: Payload => F[GeneratedToken],
  generateCode: (String, Option[Uri], Payload) => F[Code]
)(implicit S: Sync[F]) extends Http4sDsl[F]
{
  def validateRedirectUri(requestedRedirectUri: Option[Uri], configuredRedirectUri: Uri): OptionT[F, Uri] = {
    val redirectUri = requestedRedirectUri getOrElse configuredRedirectUri
    if (redirectUri == configuredRedirectUri)
      some(redirectUri)
    else
      none
  }

  def validateScopes(requestedScopes: Set[String], allowedScopes: Set[String]): OptionT[F, Set[String]] = {
    if (requestedScopes.forall(allowedScopes))
      some[F](requestedScopes).filter(_.nonEmpty)
    else
      none
  }

  def validateNonce(form: UrlForm, request: Request[F]): OptionT[F, Unit] = {
    val formNonce = form.getFirst("nonce")
    val cookieNonce = request.headers.get(Cookie).flatMap(_.values.collectFirst {
      case RequestCookie(name, content) if name == "nonce" => content
    })
    if (formNonce.exists(s => cookieNonce.contains(s)))
      some(())
    else
      none
  }

  private def toPayload(request: Request[F]): Payload =
    (for {
      principal <- request.attributes.lookup(RemoteUser)
      displayName = request.attributes.lookup(DisplayName)
      mail = request.attributes.lookup(Mail)
      entitlements = request.attributes.lookup(EntitlementsKey).getOrElse(Entitlements(List.empty))
    } yield Payload(principal, displayName, mail, entitlements)).get

  def service: HttpRoutes[F] = HttpRoutes.of [F]{
    case request @ GET -> Root =>
      val response = for {
        authorizationRequest <- OptionT(Sync[F].pure(AuthorizationRequest.fromRaw(request)))
        client <- lookupClient(authorizationRequest.clientId)
        _ <- validateScopes(authorizationRequest.scopes, client.allowedScopes)
        redirectUri <- validateRedirectUri(authorizationRequest.redirectUri, client.redirectUri)
      } yield authorizationRequest.responseType match {
        case ResponseType.Code =>
          for {
            code <- generateCode(authorizationRequest.clientId, authorizationRequest.redirectUri, toPayload(request))
            callback: Uri = redirectUri +*? code +?? ("state", authorizationRequest.state)
            response <- SeeOther(Location(callback))
          } yield response
        case ResponseType.Token =>
          for {
            token <- generateToken(toPayload(request))
            callback: Uri = redirectUri.copy(
              fragment = Some(s"access_token=${token.token.token}&token_type=Bearer&expires_in=${token.duration.getSeconds}&state=${authorizationRequest.state.getOrElse("")}")
            )
            response <- SeeOther(Location(callback))
          } yield response
      }
      response.value.flatMap(_ getOrElse Forbidden())

    case request @ POST -> Root =>
      val response = for {
        form <- EntityDecoder[F, UrlForm].decode(request, strict = true).toOption
        authorizationRequest <- OptionT(Sync[F].pure(AuthorizationRequest.fromForm(form)))
        client <- lookupClient(authorizationRequest.clientId)
        _ <- validateNonce(form, request)
        _ <- validateScopes(authorizationRequest.scopes, client.allowedScopes)
        redirectUri <- validateRedirectUri(authorizationRequest.redirectUri, client.redirectUri)
      } yield authorizationRequest.responseType match {
        case ResponseType.Code =>
          for {
            code <- generateCode(authorizationRequest.clientId, authorizationRequest.redirectUri, toPayload(request))
            callback: Uri = redirectUri +*? code +?? ("state", authorizationRequest.state)
            response <- SeeOther(Location(callback))
          } yield response
        case ResponseType.Token =>
          for {
            token <- generateToken(toPayload(request))
            callback: Uri = redirectUri.copy(
              fragment = Some(s"access_token=${token.token.token}&token_type=Bearer&expires_in=${token.duration.getSeconds}&state=${authorizationRequest.state.getOrElse("")}")
            )
            response <- SeeOther(Location(callback))
          } yield response
      }
      response.value.flatMap(_ getOrElse Forbidden())
  }
}

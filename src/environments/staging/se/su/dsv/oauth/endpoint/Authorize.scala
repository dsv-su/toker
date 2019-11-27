package se.su.dsv.oauth.endpoint

import cats.data.OptionT
import cats.data.OptionT.{none, some}
import cats.effect.Sync
import cats.syntax.all._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Cookie, Location}
import org.http4s.twirl._
import org.http4s.{HttpRoutes, Request, RequestCookie, Uri, UrlForm}
import se.su.dsv.oauth._
import se.su.dsv.oauth.environment.developerEntitlement

class Authorize[F[_]]
(
  lookupClient: String => OptionT[F, Client],
  generateToken: Payload => F[GeneratedToken],
  generateCode: (String, Option[Uri], Payload) => F[Code]
)(implicit S: Sync[F]) extends Http4sDsl[F] {

  def service: HttpRoutes[F] = HttpRoutes.of {
    case request @ GET -> Root =>
      Ok(_root_.development.html.authorize(request.params))
    case request @ POST -> Root =>
      val response = for {
        _ <- validateDeveloperAccess(request)
        form <- request.attemptAs[UrlForm].toOption
        authorizationRequest <- AuthorizationRequest.fromForm(form).toOptionT
        client <- lookupClient(authorizationRequest.clientId)
        _ <- validateNonce(form, request)
        _ <- validateScopes(authorizationRequest.scopes, client.allowedScopes)
        redirectUri <- validateRedirectUri(authorizationRequest.redirectUri, client.redirectUri)
        payload <- toPayload(request)
        callbackUri <- OptionT.liftF(authorizationRequest.responseType match {
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
        })
      } yield callbackUri
      response.value.flatMap {
        case Some(callbackUri) => SeeOther(Location(callbackUri))
        case None => Forbidden()
      }
  }

  def validateDeveloperAccess(request: Request[F]): OptionT[F, Unit] = {
    request.attributes
      .lookup(EntitlementsKey)
      .filter(_.hasEntitlement(developerEntitlement))
      .toOptionT
      .void
  }

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

  private def toPayload(request: Request[F]): OptionT[F, Payload] = {
    for {
      form <- OptionT.liftF(request.as[UrlForm])
      principal <- form.getFirst("principal").toOptionT
    } yield Payload(
      principal,
      form.getFirst("displayName"),
      form.getFirst("mail"),
      Entitlements(form.getFirst("entitlements")
        .fold(List.empty[String])(_.lines.toList.map(e => s"$entitlementPrefix:$e")))
    )
  }
}

package se.su.dsv.oauth.endpoint

import cats.data.{EitherT, OptionT}
import cats.data.OptionT.{none, some}
import cats.effect.Sync
import cats.syntax.all._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Cookie, Location}
import org.http4s.twirl._
import org.http4s.{DecodeFailure, HttpRoutes, Request, RequestCookie, Uri, UrlForm}
import se.su.dsv.oauth._
import se.su.dsv.oauth.environment.developerEntitlement

sealed trait AuthorizeError
case object NotDeveloper extends AuthorizeError
final case class BadInput(decodeFailure: DecodeFailure) extends AuthorizeError
case object InvalidAuthorizationRequest extends AuthorizeError
final case class NoSuchClient(clientId: String) extends AuthorizeError
final case class InvalidScopes(requested: Set[String], allowed: Set[String]) extends AuthorizeError
final case class InvalidRedirectUri(requested: Option[Uri], allowed: Uri) extends AuthorizeError
case object MissingPrincipal extends AuthorizeError

class Authorize[F[_]]
(
  lookupClient: String => OptionT[F, Client],
  generateToken: Payload => F[GeneratedToken],
  generateCode: (String, Option[Uri], Payload) => F[Code]
)(implicit S: Sync[F]) extends Http4sDsl[F] {

  def service: HttpRoutes[F] = HttpRoutes.of {
    case request @ GET -> Root =>
      validateDeveloperAccess(request).value flatMap {
        case Some(()) =>
          Ok(_root_.development.html.authorize(request.params))
        case None =>
          Forbidden()
      }
    case request @ POST -> Root =>
      val response = for {
        _ <- validateDeveloperAccess(request)
            .toRight[AuthorizeError](NotDeveloper)
        form <- request.attemptAs[UrlForm]
            .leftMap(BadInput)
        authorizationRequest <- AuthorizationRequest.fromForm(form)
            .toOptionT
            .toRight(InvalidAuthorizationRequest)
        client <- lookupClient(authorizationRequest.clientId)
            .toRight(NoSuchClient(authorizationRequest.clientId))
        _ <- validateScopes(authorizationRequest.scopes, client.allowedScopes)
            .toRight(InvalidScopes(
              requested = authorizationRequest.scopes,
              allowed = client.allowedScopes
            ))
        redirectUri <- validateRedirectUri(authorizationRequest.redirectUri, client.redirectUri)
            .toRight(InvalidRedirectUri(
              requested = authorizationRequest.redirectUri,
              allowed = client.redirectUri
            ))
        payload <- toPayload(form)
            .toOptionT
            .toRight(MissingPrincipal)
        callbackUri <- EitherT.liftF[F, AuthorizeError, Uri](authorizationRequest.responseType match {
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
        case Right(callbackUri) => SeeOther(Location(callbackUri))
        case Left(error) => Forbidden(error.toString)
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
      some[F](requestedScopes)
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

  private def toPayload(form: UrlForm): Option[Payload] = {
    for {
      principal <- form.getFirst("principal")
    } yield Payload(
      principal,
      form.getFirst("displayName"),
      form.getFirst("mail"),
      Entitlements(form.getFirst("entitlements")
        .fold(List.empty[String])(_.lines.toList.map(e => s"$entitlementPrefix:$e")))
    )
  }
}

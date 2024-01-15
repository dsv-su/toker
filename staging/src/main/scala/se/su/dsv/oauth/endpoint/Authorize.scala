package se.su.dsv.oauth.endpoint

import cats.data.{EitherT, OptionT}
import cats.data.OptionT.{none, some}
import cats.effect.Concurrent
import cats.syntax.all.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Cookie, Location}
import org.http4s.twirl.*
import org.http4s.{DecodeFailure, HttpRoutes, Request, RequestCookie, Uri, UrlForm}
import se.su.dsv.oauth.*
import se.su.dsv.oauth.environment.developerEntitlement

sealed trait AuthorizeError
case object NotDeveloper extends AuthorizeError
final case class BadInput(decodeFailure: DecodeFailure) extends AuthorizeError
case object InvalidAuthorizationRequest extends AuthorizeError
case object MissingPrincipal extends AuthorizeError

class Authorize[F[_] : Concurrent]
(
  lookupClient: String => OptionT[F, Client],
  generateCode: (String, Option[Uri], Payload, Option[CodeChallenge]) => F[Code]
)extends AbstractAuthorize[F](lookupClient, generateCode) {

  def service: HttpRoutes[F] = HttpRoutes.of {
    case request @ GET -> Root =>
      if (hasDeveloperAccess(request)) {
        Ok(_root_.development.html.authorize(request.params, request.attributes))
      } else {
        val response = for {
          authorizationRequest <- AuthorizationRequest.fromRaw(request).toOptionT
          payload <- getPayloadFromShibboleth(request).toOptionT
          response <- OptionT.liftF(authorize(authorizationRequest, payload))
        } yield response
        response.getOrElseF(Forbidden())
      }
    case request @ POST -> Root =>
      val response = for {
        _ <- validateDeveloperAccess(request)
        form <- request.attemptAs[UrlForm]
            .leftMap(BadInput.apply)
        authorizationRequest <- AuthorizationRequest.fromForm(form)
            .toOptionT
            .toRight(InvalidAuthorizationRequest)
        payload <- getCustomPayload(form)
            .toOptionT
            .toRight(MissingPrincipal)
        response <- EitherT.liftF(authorize(authorizationRequest, payload))
      } yield response
      response.value.flatMap {
        case Right(response) => response.pure[F]
        case Left(error) => Forbidden(error.toString)
      }
  }

  private def validateDeveloperAccess(request: Request[F]): EitherT[F, AuthorizeError, Unit] = {
    if (hasDeveloperAccess(request)) {
      EitherT.rightT(())
    } else {
      EitherT.leftT(NotDeveloper)
    }
  }

  private def hasDeveloperAccess(request: Request[F]) = {
    request.attributes
      .lookup(EntitlementsKey)
      .exists(_.hasEntitlement(developerEntitlement))
  }

  private def getCustomPayload(form: UrlForm): Option[Payload] = {
    for {
      principal <- form.getFirst("principal")
    } yield Payload(
      principal,
      form.getFirst("displayName"),
      form.getFirst("mail"),
      Entitlements(form.getFirst("entitlements")
        .fold(List.empty[String])(_.linesIterator.toList.map(e => s"$entitlementPrefix:$e")))
    )
  }

  private def getPayloadFromShibboleth(request: Request[F]): Option[Payload] =
    for {
      principal <- request.attributes.lookup(RemoteUser)
      displayName = request.attributes.lookup(DisplayName)
      mail = request.attributes.lookup(Mail)
      entitlements = request.attributes.lookup(EntitlementsKey).getOrElse(Entitlements(List.empty))
    } yield Payload(principal, displayName, mail, entitlements)
}

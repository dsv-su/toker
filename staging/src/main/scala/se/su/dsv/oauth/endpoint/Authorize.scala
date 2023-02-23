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
  generateToken: Payload => F[GeneratedToken],
  generateCode: (String, Option[Uri], Payload) => F[Code]
)extends AbstractAuthorize[F](lookupClient, generateToken, generateCode) {

  def service: HttpRoutes[F] = HttpRoutes.of {
    case request @ GET -> Root =>
      validateDeveloperAccess(request).value flatMap {
        case Some(()) =>
          Ok(_root_.development.html.authorize(request.params, request.attributes))
        case None =>
          Forbidden()
      }
    case request @ POST -> Root =>
      val response = for {
        _ <- validateDeveloperAccess(request)
            .toRight[AuthorizeError](NotDeveloper)
        form <- request.attemptAs[UrlForm]
            .leftMap(BadInput.apply)
        authorizationRequest <- AuthorizationRequest.fromForm(form)
            .toOptionT
            .toRight(InvalidAuthorizationRequest)
        payload <- toPayload(form)
            .toOptionT
            .toRight(MissingPrincipal)
        response <- EitherT.liftF(authorize(authorizationRequest, payload))
      } yield response
      response.value.flatMap {
        case Right(response) => response.pure[F]
        case Left(error) => Forbidden(error.toString)
      }
  }

  private def validateDeveloperAccess(request: Request[F]): OptionT[F, Unit] = {
    request.attributes
      .lookup(EntitlementsKey)
      .filter(_.hasEntitlement(developerEntitlement))
      .toOptionT
      .void
  }

  private def toPayload(form: UrlForm): Option[Payload] = {
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
}

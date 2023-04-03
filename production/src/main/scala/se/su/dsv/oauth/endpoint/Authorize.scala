package se.su.dsv.oauth.endpoint

import cats.data.OptionT
import cats.data.OptionT.{none, some}
import cats.effect.Concurrent
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.{Cookie, Location}
import se.su.dsv.oauth._

class Authorize[F[_] : Concurrent]
(
  lookupClient: String => OptionT[F, Client],
  generateCode: (String, Option[Uri], Payload, Option[CodeChallenge]) => F[Code]
) extends AbstractAuthorize[F](lookupClient, generateCode)
{

  private def validateNonce(form: UrlForm, request: Request[F]): OptionT[F, Unit] = {
    val formNonce = form.getFirst("nonce")
    val cookieNonce = request.headers.get[Cookie].flatMap(_.values.collectFirst {
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
        authorizationRequest <- OptionT(Concurrent[F].pure(AuthorizationRequest.fromRaw(request)))
        payload = toPayload(request)
        response <- OptionT.liftF(authorize(authorizationRequest, payload))
      } yield response
      response.foldF(Forbidden())(_.pure)

    case request @ POST -> Root =>
      val response = for {
        form <- EntityDecoder[F, UrlForm].decode(request, strict = true).toOption
        authorizationRequest <- OptionT(Concurrent[F].pure(AuthorizationRequest.fromForm(form)))
        _ <- validateNonce(form, request)
        payload = toPayload(request)
        response <- OptionT.liftF(authorize(authorizationRequest, payload))
      } yield response
      response.foldF(Forbidden())(_.pure)
  }
}

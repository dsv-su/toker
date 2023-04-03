package se.su.dsv.oauth.endpoint

import cats.effect.Concurrent
import cats.syntax.all._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.twirl._
import org.http4s.{AuthedRoutes, Uri, UrlForm}
import se.su.dsv.oauth.administration._

import scala.collection.immutable.Set

class Administration[F[_]]
  ( listClients: Principal => F[List[Client]]
  , lookupClient: (Principal, String) => F[Option[ClientDetails]]
  , registerClient: (Principal, String, String, Set[String]) => F[Client]
  , updateClient: (Principal, String, Option[String], String, String, Set[String])  => F[ClientDetails]
  )
  (implicit A: Concurrent[F])
  extends Http4sDsl[F]
{
  def service: AuthedRoutes[String, F] = AuthedRoutes.of[Principal, F] {
    case GET -> Root as principal =>
      listClients(principal) flatMap { clients => Ok(administration.html.index(clients)) }
    case GET -> Root / "client" as _ =>
      Ok(administration.html.register())
    case authedRequest @ POST -> Root / "client" as principal =>
      for {
        form <- authedRequest.req.as[UrlForm]
        newClient = for {
          name <- form.getFirst("name")
          redirectUri <- form.getFirst("redirectUri")
          scopes = form.getFirst("scopes").map(_.linesIterator.toSet).getOrElse(Set.empty)
        } yield {
          registerClient(principal, name, redirectUri, scopes) flatMap { client =>
            SeeOther(Location(Uri.unsafeFromString(s"/admin/client/${client.id}")))
          }
        }
        response <- newClient getOrElse BadRequest(administration.html.register())
      } yield response
    case GET -> Root / "client" / clientId as principal =>
      lookupClient(principal, clientId) flatMap {
        case Some(client) =>
          Ok(administration.html.client(client))
        case None =>
          NotFound()
      }
    case authedRequest @ POST -> Root / "client" / clientId as principal =>
      lookupClient(principal, clientId) flatMap {
        case Some(_) =>
          for {
            form <- authedRequest.req.as[UrlForm]
            updatedClient = for {
              name <- form.getFirst("name")
              redirectUri <- form.getFirst("redirectUri")
              secret = form.getFirst("secret")
                .filter(_.length == 32)
              scopes = form.getFirst("scopes").map(_.linesIterator.toSet).getOrElse(Set.empty)
            } yield {
              updateClient(principal, clientId, secret, name, redirectUri, scopes) flatMap { client =>
                SeeOther(Location(Uri.unsafeFromString(s"/admin/client/${client.id}")))
              }
            }
            response <- updatedClient getOrElse BadRequest()
          } yield response
        case None =>
          NotFound()
      }
  }
}

object Administration {
  type Principal = String
}
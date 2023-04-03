package se.su.dsv.oauth.endpoint

import cats.effect.Concurrent
import cats.syntax.all.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.twirl.*
import org.http4s.{AuthedRoutes, InvalidMessageBodyFailure, Uri, UrlForm}
import se.su.dsv.oauth.administration.*
import Administration._

import scala.collection.immutable.Set

class Administration[F[_]]
  ( listClients: Principal => F[List[Client]]
  , lookupClient: (Principal, String) => F[Option[ClientDetails]]
  , registerClient: (Principal, String, String, Set[String]) => F[Client]
  , updateClient: (Principal, String, Option[String], String, String, Set[String])  => F[ClientDetails]
  , registerResourceServer: (Principal, ResourceServerName) => F[ResourceServer]
  , lookupResourceServer: (Principal, ResourceServerId) => F[Option[ResourceServer]]
  , listResourceServers: Principal => F[List[ResourceServer]]
  , updateResourceServer: (Principal, ResourceServer) => F[Unit]
  )
  (implicit A: Concurrent[F])
  extends Http4sDsl[F]
{
  def service: AuthedRoutes[String, F] = AuthedRoutes.of[Principal, F] {
    case GET -> Root as principal =>
      for {
        clients <- listClients(principal)
        resourceServers <- listResourceServers(principal)
        response <- Ok(administration.html.index(clients, resourceServers))
      } yield response
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
    case GET -> Root / "resource-server" as _ =>
      Ok(administration.html.registerResourceServer())
    case authedRequest @ POST -> Root / "resource-server" as principal =>
      for {
        form <- authedRequest.req.as[UrlForm]
        name <- A.fromOption(form.getFirst("name"), InvalidMessageBodyFailure("Must supply name"))
        resourceServer <- registerResourceServer(principal, name)
        response <- SeeOther(Location(authedRequest.req.uri / resourceServer.id))
      } yield response
    case GET -> Root / "resource-server" / resourceServerId as principal =>
      lookupResourceServer(principal, resourceServerId) flatMap {
        case Some(resourceServer) =>
          Ok(administration.html.resourceServer(resourceServer))
        case None =>
          NotFound()
      }
    case authedRequest @ POST -> Root / "resource-server" / resourceServerId as principal =>
      for {
        form <- authedRequest.req.as[UrlForm]
        params = for {
          name <- form.getFirst("name") if !name.isBlank
          secret <- form.getFirst("secret") if secret.length == 32
        } yield ResourceServer(resourceServerId, name, secret)
        resourceServer <- A.fromOption(params, InvalidMessageBodyFailure("Must supply name and secret"))
        _ <- updateResourceServer(principal, resourceServer)
        response <- SeeOther(Location(authedRequest.req.uri))
      } yield response
  }
}

object Administration {
  type Principal = String
  private type ResourceServerName = String
  private type ResourceServerId = String
}
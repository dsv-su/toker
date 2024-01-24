package se.su.dsv.oauth.embedded

import cats.Functor
import cats.data.OptionT
import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import org.http4s.Uri
import se.su.dsv.oauth.{Code, CodeChallenge, Payload}

private case class Store(clients: Map[String, RegisteredClient] = Map.empty, resourceServers: Map[String, String] = Map.empty)

case class RegisteredClient(
  id: String,
  secret: Option[String],
  redirectUri: Uri
)

class InMemoryStore[F[_]: Sync] private (database: Ref[F, Store]) {
  object clients {
    def register(id: String, secret: Option[String], redirectUri: Uri): F[Unit] =
      database.update(store => store.copy(clients = store.clients.updated(id, RegisteredClient(id, secret, redirectUri))))

    def lookup(id: String): OptionT[F, RegisteredClient] =
      OptionT(database.get.map(_.clients.get(id)))
  }

  object codes {
    def generate(
      clientId: String,
      redirectUri: Option[Uri],
      payload: Payload,
      codeChallenge: Option[CodeChallenge]): F[Code] =
      for {
        uuid <- Sync[F].delay(java.util.UUID.randomUUID())
      } yield Code(redirectUri, uuid, payload, codeChallenge)
  }

  object resourceServers {
    def register(id: String, secret: String): F[Unit] =
      database.update(_.copy())
  }
}

object InMemoryStore {
  def apply[F[_]: Sync]: F[InMemoryStore[F]] =
    Ref[F].of(Store()).map(new InMemoryStore[F](_))
}

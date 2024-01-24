package se.su.dsv.oauth.embedded

import cats.Functor
import cats.data.OptionT
import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import org.http4s.Uri
import se.su.dsv.oauth.{Code, CodeChallenge, GeneratedToken, Payload, Token}

private case class Store(
  clients: Map[String, RegisteredClient] = Map.empty,
  activeCodes: Map[(String, String), Code] = Map.empty,
  resourceServers: Map[String, String] = Map.empty)

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
        code = Code(redirectUri, uuid, payload, codeChallenge)
        _ <- database.update(store =>
          store.copy(activeCodes = store.activeCodes + ((clientId, uuid.toString) -> code))
        )
      } yield code

    def lookup(clientId: String, code: String): OptionT[F, Code] =
      OptionT(database.get.map(_.activeCodes.get((clientId, code))))
  }

  object tokens {
    def generate(payload: Payload): F[GeneratedToken] =
      for {
        uuid <- Sync[F].delay(java.util.UUID.randomUUID().toString)
        token = GeneratedToken(Token(uuid), java.time.Duration.ofHours(8))
      } yield token
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

package se.su.dsv.oauth

import java.time.{Duration, Instant}
import java.util.UUID

import doobie.imports._
import org.http4s.Uri

import scala.collection.immutable.Set
import scalaz.OptionT
import scalaz.concurrent.Task

class DatabaseBackend(xa: Transactor[Task]) {
  import DatabaseBackend._

  private val TokenDuration: Duration = Duration.ofHours(1)

  def lookupClient(clientId: String): OptionT[Task, Client] =
    OptionT(queries.lookupClient(clientId)
      .option
      .transact(xa))

  def generateToken(payload: Payload): Task[GeneratedToken] =
    for {
      uuid <- Task.delay(UUID.randomUUID())
      now <- Task.delay(Instant.now())
      expiration = now.plus(TokenDuration)
      _ <- (for {
        _ <- queries.purgeExpiredTokens(now).run
        _ <- queries.storeToken(uuid, payload, expiration).run
      } yield ()).transact(xa)
    } yield GeneratedToken(Token(uuid.toString), TokenDuration)

  def generateCode(clientId: String, redirectUri: Option[Uri], payload: Payload): Task[Code] =
    for {
      uuid <- Task.delay(UUID.randomUUID())
      now <- Task.delay(Instant.now())
      expiration = now.plus(Duration.ofMinutes(1))
      _ <- (for {
        _ <- queries.purgeExpiredCodes(now).run
        _ <- queries.storeCode(clientId, redirectUri, uuid, payload, expiration).run
      } yield ()).transact(xa)
    } yield Code(redirectUri, uuid, payload)

  def lookupCode(clientId: String, uuidString: String): OptionT[Task, Code] =
    OptionT(for {
      now <- Task.delay(Instant.now())
      code <- queries.lookupCode(clientId, uuidString, now).option.transact(xa)
    } yield code)

  def getPayload(token: Token): OptionT[Task, Payload] =
    OptionT(for {
      now <- Task.delay(Instant.now())
      payload <- queries.getPayload(token, now).option.transact(xa)
    } yield payload)
}

object DatabaseBackend {
  object queries {
    def lookupClient(clientId: String): Query0[Client] =
      sql"""SELECT name, secret, scopes, redirect_uri FROM client WHERE uuid = $clientId"""
        .query[Client]

    def purgeExpiredTokens(now: Instant): Update0 =
      sql"""DELETE FROM token WHERE expires < $now"""
        .update

    def storeToken(uuid: UUID, payload: Payload, expires: Instant): Update0 =
      sql"""INSERT INTO token (uuid, expires, payload)
            VALUES ($uuid, $expires, $payload)
         """
        .update

    def purgeExpiredCodes(now: Instant): Update0 =
      sql"""DELETE FROM code WHERE expires < $now"""
        .update

    def storeCode(clientId: String, redirectUri: Option[Uri], uuid: UUID, payload: Payload, expires: Instant): Update0 =
      sql"""INSERT INTO code (client_id, redirect_uri, uuid, expires, payload)
            VALUES ($clientId, $redirectUri, $uuid, $expires, $payload)
         """
        .update

    def lookupCode(clientId: String, uuid: String, now: Instant): Query0[Code] =
      sql"""SELECT redirect_uri, uuid, payload
            FROM code
            WHERE client_id = $clientId AND uuid = $uuid AND expires > $now
         """
        .query[Code]

    def getPayload(token: Token, now: Instant): Query0[Payload] =
      sql"""SELECT payload FROM token WHERE uuid = $token AND expires > $now"""
        .query[Payload]
  }

  private[this] implicit val uriMeta: Meta[Uri] = Meta[String].nxmap(
    Uri.unsafeFromString,
    _.renderString
  )

  private[this] implicit val spaceSeparated: Meta[Set[String]] = Meta[String].nxmap(
    _.split(' ').toSet,
    _.mkString(" ")
  )

  private[this] implicit val uuidMeta: Meta[UUID] = Meta[String].nxmap(
    UUID.fromString,
    _.toString
  )
}

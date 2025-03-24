package se.su.dsv.oauth

import java.time.{Duration, Instant}
import java.util.UUID
import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.legacy.instant.*
import org.http4s.Uri
import se.su.dsv.oauth.endpoint.Introspection

import scala.collection.immutable.Set

class DatabaseBackend[F[_]](xa: Transactor[F])(implicit S: Sync[F]) {
  import DatabaseBackend._

  private val TokenDuration: Duration = Duration.ofHours(8)

  def lookupClient(clientId: String): OptionT[F, Client] =
    OptionT(queries.lookupClient(clientId)
      .map({ case ClientRow(name, maybeSecret, scopes, redirectUri) =>
        maybeSecret match {
          case Some(secret) =>
            Client.Confidential(name, secret, scopes, redirectUri)
          case None =>
            Client.Public(name, scopes, redirectUri)
        }
      })
      .option
      .transact(xa))

  def generateToken(payload: Payload): F[GeneratedToken] =
    for {
      uuid <- S.delay(UUID.randomUUID())
      now <- S.delay(Instant.now())
      expiration = now.plus(TokenDuration)
      _ <- (for {
        _ <- queries.purgeExpiredTokens(now).run
        _ <- queries.storeToken(uuid, payload, expiration).run
      } yield ()).transact(xa)
    } yield GeneratedToken(Token(uuid.toString), TokenDuration)

  def generateCode(clientId: String, redirectUri: Option[Uri], payload: Payload, codeChallenge: Option[CodeChallenge]): F[Code] =
    for {
      uuid <- S.delay(UUID.randomUUID())
      now <- S.delay(Instant.now())
      expiration = now.plus(Duration.ofMinutes(1))
      _ <- (for {
        _ <- queries.purgeExpiredCodes(now).run
        _ <- queries.storeCode(clientId, redirectUri, uuid, payload, expiration, codeChallenge).run
      } yield ()).transact(xa)
    } yield Code(redirectUri, uuid, payload, codeChallenge)

  def lookupCode(clientId: String, uuidString: String): OptionT[F, Code] =
    OptionT(for {
      now <- S.delay(Instant.now())
      code <- queries.lookupCode(clientId, uuidString, now).option.transact(xa)
    } yield code)

  def getPayload(token: Token): OptionT[F, Payload] =
    OptionT(for {
      now <- S.delay(Instant.now())
      payload <- queries.getPayload(token.token, now).option.transact(xa)
    } yield payload)

  def introspect(token: Token): F[Introspection] =
    for {
      now <- S.delay(Instant.now())
      tokenDetails <- queries.getTokenDetails(token.token).option.transact(xa)
    } yield tokenDetails match {
      case None =>
        Introspection.Inactive
      case Some(TokenDetails(expires, principal, entitlements)) if expires.isAfter(now) =>
        Introspection.Active(principal, expires, entitlements)
      case Some(_) =>
        Introspection.Inactive
    }

  def lookupResourceServerSecret(resourceServerId: String): F[Option[String]] =
    queries.lookupResourceServerSecret(resourceServerId)
      .option
      .transact(xa)
}

object DatabaseBackend {
  case class TokenDetails(expires: Instant, principal: String, entitlements: Entitlements)

  case class ClientRow(name: String, secret: Option[String], scopes: Set[String], redirectUri: Uri)

  object queries {
    def getTokenDetails(token: String): Query0[TokenDetails] =
      sql"""SELECT expires, principal, entitlements FROM token WHERE uuid = $token"""
        .query[TokenDetails]

    def lookupClient(clientId: String): Query0[ClientRow] =
      sql"""SELECT name, secret, scopes, redirect_uri FROM client WHERE uuid = $clientId"""
        .query[ClientRow]

    def purgeExpiredTokens(now: Instant): Update0 =
      sql"""DELETE FROM token WHERE expires < $now"""
        .update

    def storeToken(uuid: UUID, payload: Payload, expires: Instant): Update0 =
      sql"""INSERT INTO token (uuid, expires, principal, display_name, mail, entitlements)
            VALUES ($uuid, $expires, ${payload.principal}, ${payload.displayName}, ${payload.mail}, ${payload.entitlements})
         """
        .update

    def purgeExpiredCodes(now: Instant): Update0 =
      sql"""DELETE FROM code WHERE expires < $now"""
        .update

    def storeCode(clientId: String, redirectUri: Option[Uri], uuid: UUID, payload: Payload, expires: Instant, codeChallenge: Option[CodeChallenge]): Update0 =
      sql"""INSERT INTO code (client_id, redirect_uri, uuid, expires, principal, display_name, mail, entitlements, code_challenge)
            VALUES ($clientId, $redirectUri, $uuid, $expires, ${payload.principal}, ${payload.displayName}, ${payload.mail}, ${payload.entitlements}, $codeChallenge)
         """
        .update

    def lookupCode(clientId: String, uuid: String, now: Instant): Query0[Code] =
      sql"""SELECT redirect_uri, uuid, principal, display_name, mail, entitlements, code_challenge
            FROM code
            WHERE client_id = $clientId AND uuid = $uuid AND expires > $now
         """
        .query[Code]

    def getPayload(token: String, now: Instant): Query0[Payload] =
      sql"""SELECT principal, display_name, mail, entitlements FROM token WHERE uuid = $token AND expires > $now"""
        .query[Payload]

    def lookupResourceServerSecret(resourceServerId: String): Query0[String] =
      sql"""SELECT secret
            FROM (
              SELECT id, secret FROM resource_server
              UNION
              SELECT uuid, secret FROM client WHERE secret IS NOT NULL
            ) AS t
            WHERE id = $resourceServerId"""
        .query[String]
  }

  implicit val uriMeta: Meta[Uri] = Meta[String].imap(Uri.unsafeFromString)(_.renderString)

  implicit val spaceSeparated: Meta[Set[String]] = Meta[String].imap(_.split(' ').toSet)(_.mkString(" "))

  implicit val uuidMeta: Meta[UUID] = Meta[String].imap(UUID.fromString)(_.toString)

  implicit val entitlementsMeta: Meta[Entitlements] = Meta[String].imap(scsv => Entitlements(scsv.split(';').toList))(_.values.mkString(";"))

  implicit val codeChallengeMeta: Meta[CodeChallenge] = Meta[String]
    .imap(_.split(':') match {
      case Array("plain", challenge) => CodeChallenge.Plain(challenge)
      case Array("sha256", challenge) => CodeChallenge.Sha256(challenge)
    })({
      case CodeChallenge.Plain(challenge) => s"plain:$challenge"
      case CodeChallenge.Sha256(challenge) => s"sha256:$challenge"
    })
}

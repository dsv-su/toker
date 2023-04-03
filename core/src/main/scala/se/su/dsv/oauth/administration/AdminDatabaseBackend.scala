package se.su.dsv.oauth.administration

import cats.syntax.all._
import cats.effect.Sync
import doobie._
import doobie.implicits._

import scala.collection.immutable.Set
import scala.util.Random

class AdminDatabaseBackend[F[_]](xa: Transactor[F])(implicit S: Sync[F]) {
  import AdminDatabaseBackend._

  private def alphaNum(length: Int): F[String] = Sync[F].delay {
    val random = new Random()
    random.alphanumeric.take(length).mkString
  }

  def registerClient(owner: Principal, name: String, redirectUri: String, scopes: Set[String]): F[Client] =
    for {
      clientId <- alphaNum(36)
      clientSecret <- alphaNum(32)
      _ <- queries.insertClient(owner, clientId, Some(clientSecret), name, redirectUri, scopes)
        .run
        .transact(xa)
    } yield Client(clientId, name)


  def listClients(owner: Principal): F[List[Client]] =
    queries.listClients(owner)
      .to[List]
      .transact(xa)

  def listAllClients(): F[List[Client]] =
    queries.listAllClients
      .to[List]
      .transact(xa)

  def lookupClient(owner: Principal, clientId: String): F[Option[ClientDetails]] =
    queries.lookupClient(owner, clientId)
      .option
      .transact(xa)

  def lookupClient(clientId: String): F[Option[ClientDetails]] =
    queries.lookupClient(clientId)
      .option
      .transact(xa)

  def updateClient(owner: Principal,
                   clientId: String,
                   secret: Option[String],
                   name: String,
                   redirectUri: String,
                   scopes: Set[String]): F[ClientDetails] =
    queries.updateClient(owner, clientId, secret, name, redirectUri, scopes)
      .run
      .as(ClientDetails(clientId, name, secret, scopes, redirectUri))
      .transact(xa)

  def updateClient(clientId: String,
                   secret: Option[String],
                   name: String,
                   redirectUri: String,
                   scopes: Set[String]): F[ClientDetails] =
    queries.updateClient(clientId, secret, name, redirectUri, scopes)
      .run
      .as(ClientDetails(clientId, name, secret, scopes, redirectUri))
      .transact(xa)

  def registerResourceServer(owner: Principal, name: String): F[ResourceServer] =
    for {
      id <- alphaNum(32)
      secret <- alphaNum(32)
      _ <- queries.insertResourceServer(owner, name, id, secret).run.transact(xa)
    } yield ResourceServer(id, name, secret)

  def lookupResourceServer(owner: Principal, id: String): F[Option[ResourceServer]] =
    queries.lookupResourceServer(owner, id)
      .option
      .transact(xa)

  def listResourceServers(owner: Principal): F[List[ResourceServer]] =
    queries.listResourceServers(owner)
      .to[List]
      .transact(xa)

  def updateResourceServer(owner: Principal, resourceServer: ResourceServer): F[Unit] =
    queries.updateResourceServer(owner, resourceServer.id, resourceServer.name, resourceServer.secret)
      .run
      .void
      .transact(xa)
}

object AdminDatabaseBackend {

  object queries {
    def listAllClients: Query0[Client] =
      sql"""select uuid, name from client"""
        .query[Client]

    def listClients(owner: Principal): Query0[Client] =
      sql"""select uuid, name from client where owner = $owner"""
        .query[Client]

    def insertClient(owner: Principal, clientId: String, clientSecret: Option[String], name: String, redirectUri: String, scopes: Set[String]): Update0 =
      sql"""insert into client (owner, name, uuid, secret, redirect_uri, scopes)
            values ($owner, $name, $clientId, $clientSecret, $redirectUri, $scopes)"""
        .update

    def lookupClient(owner: Principal, clientId: String): Query0[ClientDetails] =
      sql"""select uuid, name, secret, scopes, redirect_uri
            from client
            where owner = $owner and uuid = $clientId"""
        .query[ClientDetails]

    def lookupClient(clientId: String): Query0[ClientDetails] =
      sql"""select uuid, name, secret, scopes, redirect_uri
            from client
            where uuid = $clientId"""
        .query[ClientDetails]

    def updateClient(owner: Principal,
                     clientId: String,
                     secret: Option[String],
                     name: String,
                     redirectUri: String,
                     scopes: Set[String]): Update0 =
      sql"""update client set secret = $secret, name = $name, scopes = $scopes, redirect_uri = $redirectUri
            where owner = $owner and uuid = $clientId"""
        .update

    def updateClient(clientId: String,
                     secret: Option[String],
                     name: String,
                     redirectUri: String,
                     scopes: Set[String]): Update0 =
      sql"""update client set secret = $secret, name = $name, scopes = $scopes, redirect_uri = $redirectUri
            where uuid = $clientId"""
        .update

    def insertResourceServer(owner: Principal, name: String, id: String, secret: String): Update0 =
      sql"""insert into resource_server (owner, name, id, secret) values ($owner, $name, $id, $secret)"""
        .update

    def lookupResourceServer(owner: Principal, id: String): Query0[ResourceServer] =
      sql"""select id, name, secret from resource_server where owner = $owner and id = $id"""
        .query

    def listResourceServers(owner: Principal): Query0[ResourceServer] =
      sql"""select id, name, secret from resource_server where owner = $owner"""
        .query

    def updateResourceServer(owner: Principal, id: String, name: String, secret: String): Update0 =
      sql"""update resource_server set name = $name, secret = $secret where owner = $owner and id = $id"""
        .update

    implicit val spaceSeparated: Meta[Set[String]] = Meta[String].imap(_.split(' ').toSet)(_.mkString(" "))
  }

}

package se.su.dsv.oauth.administration

import cats.effect.IO
import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import se.su.dsv.oauth.administration.AdminDatabaseBackend.queries

import scala.concurrent.ExecutionContext

class AdminDatabaseBackendSuite extends AnyFunSuite with IOChecker {

  val databaseUrl = "jdbc:h2:mem:adminbackend;DB_CLOSE_DELAY=-1;MODE=MYSQL"

  override val transactor: Transactor[IO] = Transactor.fromDriverManager[IO]("org.h2.Driver", databaseUrl)

  val flyway = Flyway.configure()
    .dataSource(databaseUrl, "", "")
    .load()
  flyway.migrate()

  test("lookup client")        { check(queries.lookupClient(null)) }
  test("lookup owned client")  { check(queries.lookupClient(null, null)) }
  test("list all clients")     { check(queries.listAllClients) }
  test("list owned clients")   { check(queries.listClients(null)) }
  test("insert client")        { check(queries.insertClient(null, null, null, null, null, Set.empty)) }
  test("update client")        { check(queries.updateClient(null, null, null, null, Set.empty)) }
  test("update owned client")  { check(queries.updateClient(null, null, null, null, null, Set.empty)) }
  test("lookup resource server") { checkOutput(queries.lookupResourceServer(false, null, null)) }
  test("list resource server")   { checkOutput(queries.listResourceServers(false, null)) }
}

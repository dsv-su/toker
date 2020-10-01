package se.su.dsv.oauth.administration

import cats.effect.{ContextShift, IO}
import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import org.scalatest._
import se.su.dsv.oauth.administration.AdminDatabaseBackend.queries

import scala.concurrent.ExecutionContext

class AdminDatabaseBackendSuite extends FunSuite with Matchers with IOChecker {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val databaseUrl = "jdbc:h2:mem:adminbackend;DB_CLOSE_DELAY=-1;MODE=MYSQL"

  override val transactor: Transactor[IO] = Transactor.fromDriverManager[IO]("org.h2.Driver", databaseUrl)

  val flyway = new Flyway()
  flyway.setDataSource(databaseUrl, "", "")
  flyway.migrate()

  test("lookup client")        { check(queries.lookupClient(null)) }
  test("lookup owned client")  { check(queries.lookupClient(null, null)) }
  test("list all clients")     { check(queries.listAllClients) }
  test("list owned clients")   { check(queries.listClients(null)) }
  test("insert client")        { check(queries.insertClient(null, null, null, null, null, Set.empty)) }
  test("update client")        { check(queries.updateClient(null, null, null, null, Set.empty)) }
  test("update owned client")  { check(queries.updateClient(null, null, null, null, null, Set.empty)) }
}

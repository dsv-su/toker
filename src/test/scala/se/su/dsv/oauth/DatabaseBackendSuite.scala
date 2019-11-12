package se.su.dsv.oauth

import cats.effect.{ContextShift, IO}
import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import org.scalatest._
import se.su.dsv.oauth.DatabaseBackend.queries

import scala.concurrent.ExecutionContext

class DatabaseBackendSuite extends FunSuite with Matchers with IOChecker {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val databaseUrl = "jdbc:h2:mem:dbs;DB_CLOSE_DELAY=-1;MODE=MYSQL"

  override val transactor: Transactor[IO] = Transactor.fromDriverManager[IO]("org.h2.Driver", databaseUrl)

  val flyway = new Flyway()
  flyway.setDataSource(databaseUrl, "", "")
  flyway.migrate()

  test("lookup client")        { check(queries.lookupClient(null)) }
  test("purge expired tokens") { check(queries.purgeExpiredTokens(null)) }
  test("store token")          { check(queries.storeToken(null, null, null)) }
  test("purge expired codes")  { check(queries.purgeExpiredCodes(null)) }
  test("store code")           { check(queries.storeCode(null, null, null, null, null)) }
  test("lookup code")          { check(queries.lookupCode(null, null, null)) }
  test("get payload")          { check(queries.getPayload(null, null)) }
}

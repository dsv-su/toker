package se.su.dsv.oauth.endpoint

import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe._
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Method, Request, Status, UrlForm}
import org.http4s.syntax.literals.uri
import org.scalatest.{Inside, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import se.su.dsv.oauth.*

import java.time.Duration
import java.util.UUID

class ExchangeSuite extends AnyWordSpec with Matchers with Inside with OptionValues {
  "Exchange endpoint" when {
    "client and code is valid" should {
      val localhost = uri"http://localhost"

      val client1 = Client("client-1", "client-1-secret", Set(), localhost)
      val client2 = Client("client-2", "client-2-secret", Set(), localhost)

      val payload = Payload("test@localhost", Some("Test Testsson"), None, Entitlements(List(s"$entitlementPrefix:test-runner")))

      val codeWithoutRedirectWithoutPKCE = Code(None, UUID.randomUUID(), payload, None)
      val codeWithRedirectWithoutPKCE = Code(Some(localhost), UUID.randomUUID(), payload, None)

      val plainProofKey = "plain-proof-key"
      val codeWithPlainPKCE = Code(None, UUID.randomUUID(), payload, Some(ProofKey.Plain(plainProofKey)))

      val token = GeneratedToken(Token("token"), Duration.ofHours(1))

      val clients = Map[String, (Client, List[Code])](
        client1.name -> (client1, List(codeWithoutRedirectWithoutPKCE, codeWithRedirectWithoutPKCE)),
        client2.name -> (client2, List(codeWithPlainPKCE)))

      val exchange = new Exchange[IO](
        clientId => OptionT.fromOption(clients.get(clientId).map(_._1)),
        (clientId, code) => OptionT.fromOption(clients.get(clientId).flatMap((_, codes) => codes.find(_.uuid.toString == code))),
        _ => token.pure
      ).service.orNotFound

      "reject exchange if redirect uri does not match" in {
        val request: Request[IO] = Request(method = Method.POST)
          .putHeaders(Authorization(BasicCredentials(client1.name, client1.secret)))
          .withEntity(UrlForm(
            ("grant_type", "authorization_code"),
            ("code", codeWithRedirectWithoutPKCE.uuid.toString)))

        val response = exchange.run(request).unsafeRunSync()

        response.status should be(Status.BadRequest)

        val json = response.as[Json].unsafeRunSync()

        inside(json.asObject.value) {
          jsonObject => inside(jsonObject("error").value) {
            error => error.asString should be(Some("invalid_grant"))
          }
        }
      }

      "exchange for token with only client secret" in {
        val request: Request[IO] = Request(method = Method.POST)
          .putHeaders(Authorization(BasicCredentials(client1.name, client1.secret)))
          .withEntity(UrlForm(
            ("grant_type", "authorization_code"),
            ("code", codeWithoutRedirectWithoutPKCE.uuid.toString)))

        val response = exchange.run(request).unsafeRunSync()

        response.status should be(Status.Ok)
      }

      "exchange for token with client secret and PKCE" in {
        val request: Request[IO] = Request(method = Method.POST)
          .putHeaders(Authorization(BasicCredentials(client2.name, client2.secret)))
          .withEntity(UrlForm(
            ("grant_type", "authorization_code"),
            ("code", codeWithPlainPKCE.uuid.toString),
            ("code_verifier", plainProofKey)))

        val response = exchange.run(request).unsafeRunSync()

        response.status should be(Status.Ok)
      }

      "decline exchange for token with faulty client secret and valid PKCE" in {
        val request: Request[IO] = Request(method = Method.POST)
          .putHeaders(Authorization(BasicCredentials(client1.name, client1.secret)))
          .withEntity(UrlForm(
            ("grant_type", "authorization_code"),
            ("code", codeWithPlainPKCE.uuid.toString),
            ("code_verifier", plainProofKey)))

        val response = exchange.run(request).unsafeRunSync()

        response.status should be(Status.BadRequest)

        val json = response.as[Json].unsafeRunSync()

        inside(json.asObject.value) {
          jsonObject =>
            inside(jsonObject("error").value) {
              error => error.asString should be(Some("invalid_grant"))
            }
        }
      }

      "exchange for token with only PKCE" in {
        val request: Request[IO] = Request(method = Method.POST)
          .putHeaders(Authorization(BasicCredentials(client2.name, "")))
          .withEntity(UrlForm(
            ("grant_type", "authorization_code"),
            ("code", codeWithoutRedirectWithoutPKCE.uuid.toString),
            ("code_verifier", plainProofKey)))

        val response = exchange.run(request).unsafeRunSync()

        response.status should be(Status.Ok)
      }
    }
  }
}

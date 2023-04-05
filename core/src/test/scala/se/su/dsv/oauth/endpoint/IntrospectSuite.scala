package se.su.dsv.oauth.endpoint

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.*
import org.http4s.headers.Authorization
import org.http4s.server.middleware.ErrorHandling
import org.http4s.{BasicCredentials, HttpVersion, Method, Request, Status, UrlForm}
import org.scalatest.{Inside, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import se.su.dsv.oauth.{Entitlements, Token}

import java.time.Instant

class IntrospectSuite extends AnyWordSpec with Matchers with Inside with OptionValues {
  "Introspection endpoint" when {
    val (resourceServerId, resourceServerSecret) = ("my-resource-server", "secret")
    val token = "active"

    val tokens = Map[String, Introspection](token -> Introspection.Active(
      "subject",
      Instant.ofEpochSecond(1680698044L),
      Entitlements(List("test-environment"))))

    val tokenLookup = (token: Token) => tokens.getOrElse(token.token, Introspection.Inactive)
    val secretLookup = Map(resourceServerId -> resourceServerSecret)

    val routes = new Introspect[IO](tokenLookup.andThen(_.pure), secretLookup.get.andThen(_.pure)).service
    val introspect = ErrorHandling(routes.orNotFound)

    "no credentials is provided" should {
      "respond with unauthorized" in {
        val requestWithoutAuthorization = Request[IO](method = Method.POST)
          .withEntity(UrlForm("token" -> token))

        val response = introspect.run(requestWithoutAuthorization)
          .unsafeRunSync()

        response.status should be(Status.Unauthorized)
      }
    }

    "credentials are provided" should {
      "respond with inactive for invalid tokens" in {
        val request = Request[IO](method = Method.POST)
          .withHeaders(Authorization(BasicCredentials(resourceServerId, resourceServerSecret)))
          .withEntity(UrlForm("token" -> "invalid"))

        val response = introspect.run(request)
          .unsafeRunSync()

        response.status should be(Status.Ok)

        val body = response.as[Json].unsafeRunSync()

        inside(body.asObject.value) { jsonObject =>
          inside(jsonObject("active").value) { active =>
            active.asBoolean.value should be(false)
          }
        }
      }

      "respond with active token" in {
        val request = Request[IO](method = Method.POST)
          .withHeaders(Authorization(BasicCredentials(resourceServerId, resourceServerSecret)))
          .withEntity(UrlForm("token" -> token))

        val response = introspect.run(request)
          .unsafeRunSync()

        response.status should be(Status.Ok)

        val body = response.as[Json].unsafeRunSync()

        inside(body.asObject.value) { jsonObject =>
          inside(jsonObject("active").value) { active =>
            active.asBoolean.value should be(true)
          }
        }
      }
    }
  }
}

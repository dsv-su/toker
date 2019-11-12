package se.su.dsv.oauth.endpoint

import java.util.UUID

import cats.data.OptionT
import cats.effect.IO
import org.http4s._
import org.http4s.argonaut._
import org.http4s.Uri.uri
import org.scalatest._
import se.su.dsv.oauth.Payload

class VerifySuite extends WordSpec with Matchers with Inside with OptionValues {
  "Verify endpoint" when {
    "no valid tokens exist" should {
      "respond with forbidden" in {
        val verify = new Verify[IO](_ => OptionT.none).service

        val response = verify(Request(
          method = Method.POST,
          uri = uri("/"),
          body = fs2.Stream(UUID.randomUUID().toString.getBytes: _*).covary[IO]
        ))

        inside(response.value.unsafeRunSync) {
          case Some(value) => value.status should be(Status.Forbidden)
        }
      }
    }

    "a valid token exist" should {
      "respond with the payload" in {
        val expected = Payload("principal")
        val token = UUID.randomUUID()
        val verify = new Verify[IO](str => OptionT.some[IO](expected).filter(_ => str.token == token.toString)).service

        val response = verify(Request(
          method = Method.POST,
          uri = uri("/"),
          body = fs2.Stream(token.toString.getBytes(): _*).covary[IO]
        ))

        inside(response.value.unsafeRunSync()) {
          case Some(Status.Ok(resp)) =>
            val payload = resp.as[Payload](implicitly, jsonOf).unsafeRunSync()
            payload should be(expected)
        }
      }
    }
  }
}

package se.su.dsv.oauth.endpoint

import java.util.UUID
import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import org.http4s._
import org.http4s.circe._
import org.http4s.syntax.literals._
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import se.su.dsv.oauth.{Entitlements, Payload}

class VerifySuite extends AnyWordSpec with Matchers with Inside with OptionValues {
  "Verify endpoint" when {
    "no valid tokens exist" should {
      "respond with forbidden" in {
        val verify = new Verify[IO](_ => OptionT.none).service

        val response = verify(Request(
          method = Method.POST,
          uri = uri"/",
          body = fs2.Stream(UUID.randomUUID().toString.getBytes: _*).covary[IO]
        ))

        inside(response.value.unsafeRunSync()) {
          case Some(value) => value.status should be(Status.Forbidden)
        }
      }
    }

    "a valid token exist" should {
      "respond with the payload" in {
        val expected = Payload("principal", Some("John Doe"), None, Entitlements(List("GDPR")))
        val token = UUID.randomUUID()
        val verify = new Verify[IO](str => OptionT.some[IO](expected).filter(_ => str.token == token.toString)).service

        val response = verify(Request(
          method = Method.POST,
          uri = uri"/",
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

  implicit val decodePayload: Decoder[Payload] = Decoder.instance(c => for {
    principal <- (c `downField` "principal").as[String]
    name <- (c `downField` "name").as[Option[String]]
    mail <- (c `downField` "mail").as[Option[String]]
    entitlements <- (c `downField` "entitlements").as[List[String]]
  } yield Payload(principal, name, mail, Entitlements(entitlements)))
}

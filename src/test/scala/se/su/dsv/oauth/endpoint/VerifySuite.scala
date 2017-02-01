package se.su.dsv.oauth.endpoint

import java.util.UUID

import org.http4s._
import org.http4s.argonaut._
import org.http4s.dsl._
import org.scalatest._
import scodec.bits.ByteVector
import se.su.dsv.oauth.Payload

import scalaz.OptionT
import scalaz.concurrent.Task
import scalaz.stream.Process

class VerifySuite extends WordSpec with Matchers with Inside {
  "Verify endpoint" when {
    "no valid tokens exist" should {
      "respond with forbidden" in {
        val verify = new Verify(_ => OptionT.none).service

        val response = verify(Request(
          method = POST,
          uri = uri("/"),
          body = Process.emit(ByteVector.fromUUID(UUID.randomUUID())).toSource
        ))

        response.unsafePerformSync.status should be(Forbidden)
      }
    }

    "a valid token exist" should {
      "respond with the payload" in {
        val expected = Payload("principal")
        val token = UUID.randomUUID()
        val verify = new Verify(str => OptionT.some[Task, Payload](expected).filter(_ => str.token == token.toString)).service

        val response = verify(Request(
          method = POST,
          uri = uri("/"),
          body = Process.emit(ByteVector(token.toString.getBytes())).toSource
        ))

        inside(response.unsafePerformSync) {
          case Ok(resp) =>
            val payload = resp.as[Payload](jsonOf).unsafePerformSync
            payload should be(expected)
        }
      }
    }
  }
}

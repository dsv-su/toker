package se.su.dsv.oauth

import io.circe.Json
import org.http4s.{Challenge, HttpVersion, MessageFailure, Response, Status}
import org.http4s.circe._
import org.http4s.headers.`WWW-Authenticate`

package object endpoint {
  object InvalidCredentials extends MessageFailure {
    override def message: String = "Invalid credentials"

    override def cause: Option[Throwable] = None

    override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
      Response(Status.Unauthorized, httpVersion)
        .withHeaders(`WWW-Authenticate`(Challenge("Basic", "toker")))
        .withEntity(Json.obj("error" -> Json.fromString("invalid_client")))
  }
}

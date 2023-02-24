package se.su.dsv.oauth

import org.http4s.{HttpVersion, MessageFailure, Response, Status}

package object endpoint {
  object InvalidCredentials extends MessageFailure {
    override def message: String = "Invalid credentials"

    override def cause: Option[Throwable] = None

    override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
      Response(Status.Unauthorized, httpVersion)
  }
}

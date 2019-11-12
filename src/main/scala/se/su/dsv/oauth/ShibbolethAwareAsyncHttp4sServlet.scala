package se.su.dsv.oauth

import cats.effect.ConcurrentEffect
import javax.servlet.http.HttpServletRequest
import org.http4s.server._
import org.http4s.servlet.{AsyncHttp4sServlet, NonBlockingServletIo, ServletIo}
import org.http4s.{HttpRoutes, ParseResult, Request}

import scala.concurrent.duration.Duration

class ShibbolethAwareAsyncHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    asyncTimeout: Duration = Duration.Inf,
    servletIo: ServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F])(implicit F: ConcurrentEffect[F])
  extends AsyncHttp4sServlet[F](service, asyncTimeout, servletIo, serviceErrorHandler)
{
  override protected def toRequest(req: HttpServletRequest): ParseResult[Request[F]] = {
    super.toRequest(req) map {
      _
        .withAttribute(se.su.dsv.oauth.RemoteUser, req.getRemoteUser)
    }
  }
}

object ShibbolethAwareAsyncHttp4sServlet {
  def apply[F[_]: ConcurrentEffect](
      service: HttpRoutes[F],
      asyncTimeout: Duration = Duration.Inf): ShibbolethAwareAsyncHttp4sServlet[F] =
    new ShibbolethAwareAsyncHttp4sServlet[F](
      service,
      asyncTimeout,
      NonBlockingServletIo[F](4096),
      DefaultServiceErrorHandler
    )
}

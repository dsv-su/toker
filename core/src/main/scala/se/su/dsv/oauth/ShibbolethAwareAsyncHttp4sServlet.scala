package se.su.dsv.oauth

import cats.effect.Async
import cats.effect.std.Dispatcher

import javax.servlet.http.HttpServletRequest
import org.http4s.server._
import org.http4s.servlet.{AsyncHttp4sServlet, NonBlockingServletIo, ServletIo}
import org.http4s.{HttpRoutes, ParseResult, Request}

import scala.annotation.nowarn
import scala.concurrent.duration.Duration

@nowarn("cat=deprecation")
class ShibbolethAwareAsyncHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    asyncTimeout: Duration = Duration.Inf,
    servletIo: ServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F],
    dispatcher: Dispatcher[F])(implicit F: Async[F])
  extends AsyncHttp4sServlet[F](service.orNotFound, asyncTimeout, servletIo, serviceErrorHandler, dispatcher)
{
  override protected def toRequest(req: HttpServletRequest): ParseResult[Request[F]] = {
    def getAttribute(attributeName: String) =
      Option(req.getAttribute(attributeName).asInstanceOf[String])
    super.toRequest(req) map { request =>
      val entitlementAttribute = getAttribute("entitlement")
        .toList
        .flatMap(_.split(';').toList)
      val a = request
        .withAttribute(RemoteUser, req.getRemoteUser)
        .withAttribute(EntitlementsKey, Entitlements(entitlementAttribute))
      val b = getAttribute("mail")
        .fold(a)(a.withAttribute(Mail, _))
      val c = getAttribute("displayName")
        .fold(b)(b.withAttribute(DisplayName, _))
      c
    }
  }
}

object ShibbolethAwareAsyncHttp4sServlet {
  def apply[F[_]: Async](
      service: HttpRoutes[F],
      asyncTimeout: Duration = Duration.Inf,
      dispatcher: Dispatcher[F]): ShibbolethAwareAsyncHttp4sServlet[F] =
    new ShibbolethAwareAsyncHttp4sServlet[F](
      service,
      asyncTimeout,
      NonBlockingServletIo[F](4096),
      DefaultServiceErrorHandler,
      dispatcher
    )
}

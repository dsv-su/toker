package se.su.dsv.oauth

import cats.effect.Async
import cats.effect.std.Dispatcher

import javax.servlet.http.HttpServletRequest
import org.http4s.server._
import org.http4s.servlet.{BlockingHttp4sServlet, BlockingServletIo, ServletIo}
import org.http4s.{HttpRoutes, ParseResult, Request}

import scala.concurrent.duration.Duration

class ShibbolethAwareHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    servletIo: BlockingServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F],
    dispatcher: Dispatcher[F])(implicit F: Async[F])
  extends BlockingHttp4sServlet[F](service.orNotFound, servletIo, serviceErrorHandler, dispatcher)
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

object ShibbolethAwareHttp4sServlet {
  def apply[F[_]: Async](
      service: HttpRoutes[F],
      dispatcher: Dispatcher[F]): ShibbolethAwareHttp4sServlet[F] =
    new ShibbolethAwareHttp4sServlet[F](
      service,
      BlockingServletIo[F](4096),
      DefaultServiceErrorHandler,
      dispatcher
    )
}

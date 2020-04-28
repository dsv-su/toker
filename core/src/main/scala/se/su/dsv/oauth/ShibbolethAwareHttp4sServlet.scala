package se.su.dsv.oauth

import cats.effect.{ConcurrentEffect, ContextShift}
import javax.servlet.http.HttpServletRequest
import org.http4s.server._
import org.http4s.servlet.{BlockingHttp4sServlet, BlockingServletIo}
import org.http4s.{HttpRoutes, ParseResult, Request}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class ShibbolethAwareHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    asyncTimeout: Duration = Duration.Inf,
    servletIo: BlockingServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F])(implicit F: ConcurrentEffect[F])
  extends BlockingHttp4sServlet[F](service, servletIo, serviceErrorHandler)
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
  def apply[F[_]: ConcurrentEffect: ContextShift](
      service: HttpRoutes[F],
      blockingEc: ExecutionContext,
      asyncTimeout: Duration = Duration.Inf): ShibbolethAwareHttp4sServlet[F] =
    new ShibbolethAwareHttp4sServlet[F](
      service,
      asyncTimeout,
      BlockingServletIo[F](4096, blockingEc),
      DefaultServiceErrorHandler
    )
}

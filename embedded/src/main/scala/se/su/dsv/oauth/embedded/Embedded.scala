package se.su.dsv.oauth.embedded

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import org.http4s.HttpRoutes
import org.http4s.server.AuthMiddleware
import org.http4s.server.middleware.CORS
import se.su.dsv.oauth.{RemoteUser, ShibbolethAwareHttp4sServlet}
import se.su.dsv.oauth.endpoint.{Administration, Authorize, Exchange, Introspect, Verify}

import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener, ServletRegistration}
import javax.servlet.annotation.WebListener

@WebListener
class Embedded extends ServletContextListener {
  private var shutdown: IO[Unit] = IO.unit

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val resources = for {
      dispatcher <- Dispatcher.parallel[IO]
    } yield dispatcher

    val (dispatcher, deallocate) = resources.allocated.unsafeRunSync()
    this.shutdown = deallocate

    val ctx = sce.getServletContext
    
    mountService(ctx,
      name = "authorize",
      service = new Authorize[IO](???, ???).service,
      mapping = "/authorize")

    mountService(ctx,
      name = "exchange",
      service = CORS.policy
        .apply(new Exchange[IO](???, ???, ???).service),
      mapping = "/exchange")

    mountService(ctx,
      name = "verify",
      service = new Verify[IO](???).service,
      mapping = "/verify")

    mountService(ctx,
      name = "introspect",
      service = new Introspect[IO](???, ???).service,
      mapping = "/introspect")

    val remoteUserAuthentication = AuthMiddleware[IO, String](Kleisli(
      req => OptionT.fromOption(req.attributes.lookup(RemoteUser))))

    mountService(ctx,
      name = "administration",
      service = remoteUserAuthentication(new Administration[IO](
        listClients = ???,
        lookupClient = ???,
        registerClient = ???,
        updateClient = ???,
        registerResourceServer = ???,
        lookupResourceServer = ???,
        listResourceServers = ???,
        updateResourceServer = ???
      ).service),
      mapping = "/admin/*")

    def mountService(
      self: ServletContext,
      name: String,
      service: HttpRoutes[IO],
      mapping: String = "/*"): ServletRegistration.Dynamic = {
      val servlet = ShibbolethAwareHttp4sServlet(
        service = service,
        dispatcher = dispatcher
      )
      val reg = self.addServlet(name, servlet)
      reg.setLoadOnStartup(1)
      reg.setAsyncSupported(true)
      reg.addMapping(mapping)
      reg
    }
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    shutdown.unsafeRunSync()
  }
}

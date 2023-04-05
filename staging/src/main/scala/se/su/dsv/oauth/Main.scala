package se.su.dsv.oauth

import cats.data.{Kleisli, OptionT}
import cats.effect.std.Dispatcher
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.util.transactor.Transactor

import javax.naming.InitialContext
import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener, ServletRegistration}
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.http4s.{HttpRoutes, Request}
import org.http4s.server.AuthMiddleware
import se.su.dsv.oauth.endpoint.*

import scala.concurrent.ExecutionContext

@WebListener
class Main extends ServletContextListener {
  var deallocate: IO[Unit] = IO.pure(())
  var dispatcher: Dispatcher[IO] = _

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val ctx = sce.getServletContext
    val dataSource = InitialContext.doLookup[DataSource]("java:comp/env/jdbc/oauthDS")

    val flyway = Flyway.configure()
      .dataSource(dataSource)
      .table("schema_version")
      .load()
    flyway.migrate()

    val connectEC = ExecutionContext.global
    val tx = Transactor.fromDataSource[IO](dataSource, connectEC)

    val backend = new DatabaseBackend(tx)
    val adminBackend = new administration.AdminDatabaseBackend(tx)

    val (dispatcher, deallocate) = Dispatcher.parallel[IO].allocated.unsafeRunSync()
    this.dispatcher = dispatcher
    this.deallocate = deallocate

    mountService(ctx,
      name = "authorize",
      service = new Authorize(backend.lookupClient, backend.generateCode).service,
      mapping = "/authorize")

    mountService(ctx,
      name = "exchange",
      service = new Exchange(backend.lookupClient, backend.lookupCode, backend.generateToken).service,
      mapping = "/exchange")

    mountService(ctx,
      name = "verify",
      service = new Verify(backend.getPayload).service,
      mapping = "/verify")
    
    mountService(ctx,
      name = "introspect",
      service = new Introspect(backend.introspect, backend.lookupResourceServerSecret).service,
      mapping = "/introspect")

    val remoteUserAuthentication = AuthMiddleware[IO, String](Kleisli(
      req => OptionT.fromOption(req.attributes.lookup(RemoteUser))))

    mountService(ctx,
      name = "administration",
      service = remoteUserAuthentication(new Administration(
        listClients = adminBackend.listClients,
        lookupClient = adminBackend.lookupClient,
        registerClient = adminBackend.registerClient,
        updateClient = adminBackend.updateClient,
        registerResourceServer = adminBackend.registerResourceServer,
        lookupResourceServer = adminBackend.lookupResourceServer(false),
        listResourceServers = adminBackend.listResourceServers(false),
        updateResourceServer = adminBackend.updateResourceServer(false)
      ).service),
      mapping = "/admin/*")

    ()
  }

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

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    deallocate.unsafeRunSync()
  }
}

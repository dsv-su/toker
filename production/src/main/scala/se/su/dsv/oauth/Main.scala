package se.su.dsv.oauth

import cats.data.{Kleisli, OptionT}
import cats.effect.{ConcurrentEffect, ContextShift, IO}
import doobie.util.transactor.Transactor
import javax.naming.InitialContext
import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener, ServletRegistration}
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.http4s.server.AuthMiddleware
import org.http4s.{HttpRoutes, Request}
import se.su.dsv.oauth.endpoint._

import scala.concurrent.ExecutionContext

@WebListener
class Main extends ServletContextListener {
  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val ctx = sce.getServletContext
    val dataSource = InitialContext.doLookup[DataSource]("java:comp/env/jdbc/oauthDS")

    val flyway = new Flyway
    flyway.setDataSource(dataSource)
    flyway.migrate()

    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val connectEC = ExecutionContext.global
    val transactEC = ExecutionContext.global
    val tx = Transactor.fromDataSource[IO](dataSource, connectEC, transactEC)

    val backend = new DatabaseBackend(tx)
    val adminBackend = new administration.AdminDatabaseBackend(tx)

    mountService(ctx,
      name = "authorize",
      service = new Authorize(backend.lookupClient, backend.generateToken, backend.generateCode).service,
      mapping = "/authorize")

    mountService(ctx,
      name = "exchange",
      service = new Exchange(backend.lookupClient, backend.lookupCode, backend.generateToken).service,
      mapping = "/exchange")

    mountService(ctx,
      name = "verify",
      service = new Verify(backend.getPayload).service,
      mapping = "/verify")

    val remoteUserAuthentication = AuthMiddleware(Kleisli[OptionT[IO, ?], Request[IO], String](
      req => OptionT.fromOption(req.attributes.lookup(RemoteUser))))

    mountService(ctx,
      name = "administration",
      service = remoteUserAuthentication(new Administration(
        listClients = Function.const(adminBackend.listAllClients),
        lookupClient = (_, clientId) => adminBackend.lookupClient(clientId),
        registerClient = adminBackend.registerClient,
        updateClient = (_, clientId, secret, name, redirectUri, scopes) =>
          adminBackend.updateClient(clientId, secret, name, redirectUri, scopes)
      ).service),
      mapping = "/admin/*")

    ()
  }

  def mountService(
      self: ServletContext,
      name: String,
      service: HttpRoutes[IO],
      mapping: String = "/*")(implicit CE: ConcurrentEffect[IO]): ServletRegistration.Dynamic = {
    val servlet = ShibbolethAwareAsyncHttp4sServlet(
      service = service,
    )
    val reg = self.addServlet(name, servlet)
    reg.setLoadOnStartup(1)
    reg.setAsyncSupported(true)
    reg.addMapping(mapping)
    reg
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = ()
}

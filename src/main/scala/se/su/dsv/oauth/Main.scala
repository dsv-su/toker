package se.su.dsv.oauth

import cats.effect.IO
import javax.naming.InitialContext
import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener, ServletRegistration}
import javax.servlet.annotation.WebListener
import javax.sql.DataSource
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import org.http4s.HttpService
import org.http4s.servlet._
import se.su.dsv.oauth.endpoint._

@WebListener
class Main extends ServletContextListener {
  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val ctx = sce.getServletContext
    val dataSource = InitialContext.doLookup[DataSource]("java:comp/env/jdbc/oauthDS")

    val flyway = new Flyway
    flyway.setDataSource(dataSource)
    flyway.migrate()

    val backend = new DatabaseBackend(Transactor.fromDataSource[IO](dataSource))

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

    ()
  }

  def mountService(self: ServletContext, name: String, service: HttpService[IO], mapping: String = "/*"): ServletRegistration.Dynamic = {
    val servlet = Http4sServlet2(
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

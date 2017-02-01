package se.su.dsv.oauth

import javax.naming.InitialContext
import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener, ServletRegistration}
import javax.servlet.annotation.WebListener
import javax.sql.DataSource

import doobie.util.transactor.DataSourceTransactor
import org.flywaydb.core.Flyway
import org.http4s.HttpService
import org.http4s.server.AsyncTimeoutSupport
import org.http4s.servlet._
import se.su.dsv.oauth.endpoint._
import org.http4s.servlet.syntax._

import scalaz.concurrent.{Strategy, Task}

@WebListener
class Main extends ServletContextListener {
  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val ctx = sce.getServletContext
    val dataSource = InitialContext.doLookup[DataSource]("java:comp/env/jdbc/oauthDS")

    val flyway = new Flyway
    flyway.setDataSource(dataSource)
    flyway.migrate()

    val backend = new DatabaseBackend(DataSourceTransactor[Task](dataSource))

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

  def mountService(self: ServletContext, name: String, service: HttpService, mapping: String = "/*"): ServletRegistration.Dynamic = {
    val servlet = new Http4sServlet2(
      service = service,
      asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
      threadPool = Strategy.DefaultExecutorService,
      servletIo = servletIo(self)
    )
    val reg = self.addServlet(name, servlet)
    reg.setLoadOnStartup(1)
    reg.setAsyncSupported(true)
    reg.addMapping(mapping)
    reg
  }

  private def servletIo(self: ServletContext): ServletIo = {
    val version = ServletApiVersion(self.getMajorVersion, self.getMinorVersion)
    if (version >= ServletApiVersion(3, 1))
      NonBlockingServletIo(4096)
    else
      BlockingServletIo(4096)
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = ()
}

package se.su.dsv.oauth

import cats.effect.{ConcurrentEffect, ContextShift, IO}
import doobie.util.transactor.Transactor
import io.jaegertracing.{Configuration => JaegerConfiguration}
import io.opentracing.contrib.web.servlet.filter.TracingFilter
import javax.naming.InitialContext
import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener, ServletRegistration}
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.http4s.HttpRoutes
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

    val serviceNameConfiguration = sys.props.get(JaegerConfiguration.JAEGER_SERVICE_NAME).orElse(sys.env.get(JaegerConfiguration.JAEGER_SERVICE_NAME))
    if (serviceNameConfiguration.isDefined) {
      val tracer = JaegerConfiguration.fromEnv()
        .getTracer
      ctx.addFilter("tracing-filter", new TracingFilter(tracer))
        .addMappingForUrlPatterns(null, true, "/*")
      ctx.log(s"Tracing using $tracer")
    }
    else {
      ctx.log("Not tracing")
    }

    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val connectEC = ExecutionContext.global
    val transactEC = ExecutionContext.global

    val backend = new DatabaseBackend(Transactor.fromDataSource[IO](dataSource, connectEC, transactEC))

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

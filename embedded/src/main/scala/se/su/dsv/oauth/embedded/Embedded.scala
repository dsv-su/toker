package se.su.dsv.oauth.embedded

import cats.Applicative
import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, Resource}
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.http4s.{HttpRoutes, Uri}
import org.http4s.server.AuthMiddleware
import org.http4s.server.middleware.CORS
import se.su.dsv.oauth.{Client, RemoteUser, ShibbolethAwareHttp4sServlet}
import se.su.dsv.oauth.endpoint.{Administration, DeveloperCustomAuthorize, Exchange, Introspect, Verify}

import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener, ServletRegistration}
import javax.servlet.annotation.WebListener

@WebListener
class Embedded extends ServletContextListener {
  private var shutdown: IO[Unit] = IO.unit

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val resources = for {
      dispatcher <- Dispatcher.parallel[IO]
      backend <- Resource.eval(for {
        store <- InMemoryStore[IO]

        // Check environment variables and register clients and resource servers
        _ <- (for {
          id <- sys.env.get("CLIENT_ID")
          secret = sys.env.get("CLIENT_SECRET")
          uriString <- sys.env.get("CLIENT_REDIRECT_URI")
        } yield parseUri(uriString).map(uri => store.clients.register(id, secret, uri))).orEmpty
        _ <- (for {
          id <- sys.env.get("RESOURCE_SERVER_ID")
          secret <- sys.env.get("RESOURCE_SERVER_SECRET")
        } yield store.resourceServers.register(id, secret)).orEmpty
      } yield store)
    } yield (dispatcher, backend)

    val ((dispatcher, backend), deallocate) = resources.allocated.unsafeRunSync()
    this.shutdown = deallocate

    val ctx = sce.getServletContext

    mountService(ctx,
      name = "authorize",
      service = new DeveloperCustomAuthorize[IO](
        lookupClient = clientId => for {
          client <- backend.clients.lookup(clientId)
        } yield client.secret match {
          case Some(secret) => Client.Confidential(client.id, secret, Set.empty, client.redirectUri)
          case None => Client.Public(client.id, Set.empty, client.redirectUri)
        },
        generateCode = (clientId, redirectUri, payload, codeChallenge) => for {
          code <- backend.codes.generate(clientId, redirectUri, payload, codeChallenge)
        } yield code).service,
      mapping = "/authorize")

    mountService(ctx,
      name = "exchange",
      service = CORS.policy
        .apply(new Exchange[IO](
          lookupClient = clientId => for {
            client <- backend.clients.lookup(clientId)
          } yield client.secret match {
            case Some(secret) => Client.Confidential(client.id, secret, Set.empty, client.redirectUri)
            case None => Client.Public(client.id, Set.empty, client.redirectUri)
          },
          lookupCode = backend.codes.lookup,
          generateToken = backend.tokens.generate).service),
      mapping = "/exchange")

    mountService(ctx,
      name = "verify",
      service = new Verify[IO](lookupToken = backend.tokens.lookup).service,
      mapping = "/verify")

    mountService(ctx,
      name = "introspect",
      service = new Introspect[IO](???, ???).service,
      mapping = "/introspect")

    def mountService(
      self: ServletContext,
      name: String,
      service: HttpRoutes[IO],
      mapping: String = "/*"): ServletRegistration.Dynamic = {
      val servlet = ShibbolethAwareHttp4sServlet[IO](
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

  private def parseUri(uri: String): IO[Uri] = IO.fromEither(Uri.fromString(uri))

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    shutdown.unsafeRunSync()
  }
}

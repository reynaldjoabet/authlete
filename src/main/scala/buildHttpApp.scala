// import cats.effect._

// import config.JwtConfig
// import org.http4s.ember.client.EmberClientBuilder
// import org.http4s.server.Router
// import org.http4s.HttpApp
// import org.http4s.HttpRoutes
// //import services.SecurityMiddleware

// def buildHttpApp(cfg: JwtConfig, routes: HttpRoutes[IO]): Resource[IO, HttpApp[IO]] =
//   for {
//     client  <- EmberClientBuilder.default[IO].build
//     jwks    <- JwksProvider.resource(cfg, client)
//     key     <- Resource.eval(SecurityMiddleware.PrincipalKeyF)
//     verifier = new JwtVerifier(cfg, jwks)

//     securedRoutes =
//       SecurityMiddleware.authenticate(verifier, key)(
//         SecurityMiddleware.requireScopes(key, Set("read:items"))(routes)
//       )
//   } yield Router("/" -> securedRoutes).orNotFound

object buildHttpApp {}

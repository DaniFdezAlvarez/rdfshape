package es.weso.server
import cats.effect._

import scala.util.Properties.envOrNone

/*
class HelloService[F[_]](blocker: Blocker)(implicit F: Effect[F], cs: ContextShift[F])
  extends Http4sDsl[F] {

  def routes(implicit timer: Timer[F]): HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root =>
      Ok("Hi!")
  }
}

object HelloService {
  def apply[F[_]: Effect: ContextShift](blocker: Blocker): HelloService[F] =
    new HelloService[F](blocker)
}
 */

/**
  * RDFShape server
 **/
/* class RDFShapeServer[F[_]:ConcurrentEffect: Timer](host: String, port: Int)(implicit F: Effect[F], cs: ContextShift[F]) {
  private val logger = getLogger

  logger.info(s"Starting RDFShape on '$host:$port'")

/*  def routesService(blocker: Blocker, client: Client[F]): HttpRoutes[F] =
    HelloService[F](blocker).routes */
    /*  CORS(
        WebService[F](blocker).routes <+>
        DataService[F](blocker, client).routes <+>
        WikidataService[F](blocker, client).routes <+>
        ShExService[F](blocker,client).routes <+>
        SchemaService[F](blocker,client).routes <+>
        ShapeMapService[F](blocker,client).routes <+>
        APIService[F](blocker, client).routes <+>
        EndpointService[F](blocker).routes <+>
        LinksService[F](blocker).routes
      ) */

  /*  val service = routesService.local { req: Request[IO] =>
      val path = req.uri.path
      logger.debug(s"Request with path: ${req.remoteAddr.getOrElse("null")} -> ${req.method}: $path")
      req
    } */

  /*  def build(): fs2.Stream[IO, ExitCode] =
      BlazeServerBuilder[IO].bindHttp(port, host)
        .mountService(service).
        serve */

/*  def httpApp(blocker: Blocker,
              client: Client[F]): HttpApp[F] =
   routesService(blocker, client).orNotFound
  //  TestRoutes.helloWorldRoutes[F](HelloWorld.impl[F]).orNotFound

  def resource: Resource[F, Server[F]] =
    for {
      blocker <- Blocker[F]
      client <- BlazeClientBuilder[F](global).resource
      server <- BlazeServerBuilder[F]
        .bindHttp(port)
        .withHttpApp(httpApp(blocker,mkClient(client)))
        .resource
    } yield server
 */
//  def stream[F[_]: ConcurrentEffect](blocker:Blocker)(implicit T: Timer[F], C: ContextShift[F]): Stream[F, Nothing] = {
} */

object RDFShapeServer extends IOApp {

  private val ip   = "0.0.0.0"
  private val port = envOrNone("PORT") map (_.toInt) getOrElse 8080
  println(s"PORT: $port")

  override def run(args: List[String]): IO[ExitCode] = {
    Server.stream(port, ip).compile.drain.as(ExitCode.Success)
  }

}

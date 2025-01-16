package backend.server

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directive0, Directives}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object BackendServer {

  def start(interface: String,
            port: Int,
            system: ActorSystem[_],
            grpcService: backend.proto.BackendService): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContextExecutor = system.executionContext

    val service = ServiceHandler.concatOrNotFound(
      backend.proto.BackendServiceHandler.partial(grpcService),
      // ServerReflection enabled to support grpcurl without import-path and proto parameters
      ServerReflection.partial(List(backend.proto.BackendService))
    )

    val route = Directives.concat(
      Directives.handle(service)
    )

    val bound = Http()
      .newServerAt(interface, port)
      .bind( service )
      .map(_.addToCoordinatedShutdown(3.seconds))


    bound.onComplete{
      case Success(binding) =>
        system.log.info("BackendService online at gRPC server {}:{}",binding.localAddress.getHostString,binding.localAddress.getPort)
      case Failure(error) =>
        system.log.error("Failed to bind gRPC server, shutting down!", error)
        system.terminate()
    }

  }

}

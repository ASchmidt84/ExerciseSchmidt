package backend

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import backend.entity.BlogEntry
import backend.external.DataCollector
import backend.projection.{BlogEntryGeneratorHandler, BlogEntryProjection}
import backend.server.BackendServer
import backend.service.BackendServiceImpl
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

object Main {

  private val logger = LoggerFactory.getLogger("backend.main")

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](Behaviors.empty, "backend-service")
    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  private def init(system: ActorSystem[_]): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    //Entität init
    BlogEntry.init(system)

    //Repos aufbauen
    //Gibt es nicht
    // Projections also Events innerhalb des Systems
    BlogEntryProjection.init(system,system.settings.config)

    val generator = new BlogEntryGeneratorHandler()(system)


    //Events publish / consume ()

    // Interface

    val grpcInterface = system.settings.config.getString("backend-service.grpc.interface")
    val grpcPort = system.settings.config.getInt("backend-service.grpc.port")
    val grpcService = new BackendServiceImpl(system)

    BackendServer.start(
      grpcInterface, grpcPort,
      system,
      grpcService
    )


    system
      .scheduler
      .scheduleWithFixedDelay(5.seconds,5.minutes){ () => {
        import system._
        DataCollector.callBlogEntries(system.settings.config.getString("url.blogentry"))(system.executionContext)
          .flatMap{seq =>
            Future.sequence(seq.map(i => generator.insertBlogEntry(i)))
          }
          .map(_ => Done)
      }}(system.executionContext)

  }

}

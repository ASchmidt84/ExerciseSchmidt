package backend.projection

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey}
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import backend.entity.BlogEntry
import io.grpc.Status

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, TimeoutException}
import scala.concurrent.duration.DurationInt

/**
 * Trait zur Vermeidung von Redundaanzen!
 */
trait EntityAccess {

  protected implicit val system: ActorSystem[_]
  implicit protected val ec: ExecutionContextExecutor =  system.executionContext
  protected implicit val timeout: Timeout = Timeout(30.seconds)
  protected val sharding = ClusterSharding(system)
  protected val entityKey: EntityTypeKey[BlogEntry.Command] = BlogEntry.EntityKey

  protected def convertError[T](response: Future[T])(implicit ec: ExecutionContext): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(Status.UNAVAILABLE.withDescription("Operation timed out"))
        )
      case exc =>
        Future.failed(
          new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(exc.getMessage))
        )
    }
  }

  protected def entity(entityId: String): EntityRef[BlogEntry.Command] = sharding.entityRefFor(
    entityKey,
    entityId.trim.toLowerCase
  )


}

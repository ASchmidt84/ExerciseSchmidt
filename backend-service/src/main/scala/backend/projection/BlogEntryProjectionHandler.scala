package backend.projection

import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.persistence.query.typed.EventEnvelope
import akka.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}
import backend.entity.BlogEntry
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey}
import akka.grpc.GrpcServiceException
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, TimeoutException}
import scala.concurrent.duration.DurationInt

class BlogEntryProjectionHandler(slice: String,
                                 implicit val system: ActorSystem[_],
                                 targetUrl: String) extends R2dbcHandler[EventEnvelope[BlogEntry.Event]] {
  private val logger = LoggerFactory.getLogger("BlogEntry.ProjectionHandler")


  implicit private val ec: ExecutionContextExecutor =  system.executionContext
  private implicit val timeout: Timeout = Timeout(30.seconds)
  private val sharding = ClusterSharding(system)
  private val entityKey: EntityTypeKey[BlogEntry.Command] = BlogEntry.EntityKey

  private def convertError[T](response: Future[T])(implicit ec: ExecutionContext): Future[T] = {
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

  private def entity(entityId: String): EntityRef[BlogEntry.Command] = sharding.entityRefFor(
    entityKey,
    entityId.trim.toLowerCase
  )

  override def process(session: R2dbcSession,
                       envelope: EventEnvelope[BlogEntry.Event]): Future[Done] = envelope.event match {
    case BlogEntry.Created(_,_,_,_,entityId,_) =>
      convertError(entity(entityId).askWithStatus(reply => BlogEntry.Get(reply)))
        .map{
          summary =>
            val sinkMsg: Sink[Message, Future[Done]] = {
              Sink.foreach { _ =>
                // alle nachrichten ignorieren ist kein Incoming!
              }
            }
            val sourceMsg: Source[Message, NotUsed] = Source
              .single(TextMessage(Json.toJson(summary.countedMapScala).toString()))
            val flow: Flow[Message, Message, Future[Done]] = Flow
              .fromSinkAndSourceMat(sinkMsg, sourceMsg)(Keep.left)
            val (upgradeResponse, closed) = Http()
              .singleWebSocketRequest(WebSocketRequest(targetUrl), flow)

            val connected = upgradeResponse.map { upgrade =>
              if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
                Done
              } else {
                throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
              }
            }

            // Normalerweise hier mehr Fehlerhandling
            connected.onComplete(_ => logger.info("Done"))
            closed.foreach(_ => logger.info("closed"))


            Done
        }
        .recover{
          case j =>
            logger.error("Error while processing", j)
            Done
        }


    case _ => Future.successful(Done)
  }

}
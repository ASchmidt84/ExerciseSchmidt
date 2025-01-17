package backend.projection

import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.persistence.query.typed.EventEnvelope
import akka.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}
import backend.entity.BlogEntry
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import scala.concurrent.Future

/**
 * Erstellt hier keine DB Projektionen, sondern sorgt dafür,
 * dass an das Frontend etwas geschickt wird.
 * @param slice ignoriert werden
 * @param system
 * @param targetUrl Zielurl für den Webservice
 */
class BlogEntryProjectionHandler(slice: String,
                                 protected implicit val system: ActorSystem[_],
                                 targetUrl: String) extends R2dbcHandler[EventEnvelope[BlogEntry.Event]] with EntityAccess {
  private val logger = LoggerFactory.getLogger("BlogEntry.ProjectionHandler")

  /**
   * Ist die Methode, die das Verhalten implementiert und bei einem Event aufgerufen wird
   * @param session
   * @param envelope
   * @return
   */
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
              .single(TextMessage.Strict(Json.toJson(summary.countedMapScala).toString()))

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
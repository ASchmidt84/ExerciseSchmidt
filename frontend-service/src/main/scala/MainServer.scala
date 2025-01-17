import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route.seal
import akka.stream.scaladsl.{Flow, Sink, Source}
import play.api.libs.json.Json

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn

object MainServer {

  implicit val system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  private val blogEntryBuffer = mutable.Buffer[Map[String,Int]]()
  private def blogEntryMapToHtml: String = {
    blogEntryBuffer.map{item =>
      s"""
         |<div style="border-top:1px solid black;margin-bottom:3em;">
         |<ul>
         |${item.map(data => s"<li>${data._1}: ${data._2}x</li>")}
         |</ul>
         |</div>
         |""".stripMargin
    }.mkString
  }


  val blogEntryWebSocketService: Flow[Message,Message,Any] = Flow[Message]
      .collect {
        case TextMessage.Strict(msg) =>
          Future.successful(Json.parse(msg).as[Map[String,Int]])
        case TextMessage.Streamed(stream) => stream
          .limit(100)                   // Max frames we are willing to wait for
          .completionTimeout(5.seconds) // Max time until last frame
          .runFold("")(_ + _)           // Merges the frames
          .flatMap(msg => Future.successful(Json.parse(msg).as[Map[String,Int]]))

        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
    .mapAsync(parallelism = 3)(identity)
    .via(Flow[Map[String,Int]].map{ data =>
      blogEntryBuffer += data
      "Done"
    } )
    .map {
      case msg: String ⇒ TextMessage.Strict(msg)
    }

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          s"""
            |<html>
            |<body>
            |<h1>Daten Map</h1>
            |$blogEntryMapToHtml
            |</body>
            |</html>
            |""".stripMargin
        )
      )

    case req @ HttpRequest(GET, Uri.Path("/blogentry/push"), _, _, _) =>
      req.attribute(AttributeKeys.webSocketUpgrade) match {
        case Some(upgrade) => upgrade.handleMessages(blogEntryWebSocketService)
        case None          => HttpResponse(400, entity = "Not a valid websocket request!")
      }

    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  def main(array: Array[String]): Unit = {
    val bindingFuture = Http().newServerAt("localhost", 9000).bindSync(requestHandler)
    println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}

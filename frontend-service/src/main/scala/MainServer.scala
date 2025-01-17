import akka.actor.ActorRef
import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route.seal
import akka.persistence.r2dbc.state.scaladsl.AdditionalColumn.Skip
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.reactivestreams.Publisher
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
         |<div class="data" style="border-top:1px solid black;margin-bottom:3em;">
         |${item.mkString("(","), (","), ")}
         |</div>
         |""".stripMargin
    }.mkString
  }


  val blogEntryWebSocketService: Flow[Message,Message,NotUsed] = Flow[Message]
    .mapConcat{
      case TextMessage.Strict(msg) =>
        blogEntryBuffer += Json.parse(msg).as[Map[String,Int]]
        TextMessage(Source.single("Danke")) :: Nil
      case _ =>
        TextMessage(Source.single("Danke")) :: Nil
    }

  val routes = {
    concat (
      get {
        concat(
          pathSingleSlash {
            complete(HttpResponse(
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
            ))
          },
          path("blogentry" / "size") {
            complete(HttpResponse(
              entity = HttpEntity(
                ContentTypes.`text/plain(UTF-8)`,
                blogEntryBuffer.size.toString
              )
            ))
          }
        )
      },
      path("blogentry"/"push") {
        handleWebSocketMessages( blogEntryWebSocketService )
      }
    )
  }

  def main(array: Array[String]): Unit = {
    val bindingFuture = Http().newServerAt("localhost", 9000).bind(routes) //.bindSync(requestHandler)
    println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}

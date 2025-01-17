import akka.http.scaladsl.model.{ContentTypes, HttpRequest, StatusCodes}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.ws.TextMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.concurrent.duration.DurationInt

class WebServiceTest extends AnyWordSpec with Matchers with ScalatestRouteTest {

//  MainServer.main(Array.empty)

  "/" should {
    "deliver a Website with <h1>Daten Map</h1>" in {
      val request = HttpRequest(uri = "/")
      request ~> MainServer.routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`text/html(UTF-8)`)
        entityAs[String] should include ("<h1>Daten Map</h1>")
        entityAs[String] should not include ("<div class=\"data\"")
      }
    }
  }

  "No Blogentry" should {
    "be their now, cause no push is Done!" in {
      HttpRequest(uri = "/blogentry/size") ~> MainServer.routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`text/plain(UTF-8)`)
        entityAs[String] should ===("0")
      }
    }
  }

  "Push a BlogEntry" should {

    "should first be 0" in {
      HttpRequest(uri = "/blogentry/size") ~> MainServer.routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`text/plain(UTF-8)`)
        entityAs[String] should ===("0")
      }
    }

    "Works" in {
      val wsClient = WSProbe()
      WS("/blogentry/push", wsClient.flow) ~> MainServer.routes ~> check {
        isWebSocketUpgrade shouldEqual true
        wsClient.sendMessage(TextMessage.Strict( Json.toJson(Map[String,Int]("H" -> 3)).toString() ))
        wsClient.expectMessage("Danke")
        wsClient.sendCompletion()
      }
    }

    "now it should be 1" in {
      HttpRequest(uri = "/blogentry/size") ~> MainServer.routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`text/plain(UTF-8)`)
        entityAs[String] should ===("1")
      }
    }

  }

  "routing-example" in {
//    val a = Http().newServerAt("localhost", 9000).bindSync(MainServer.requestHandler)
//    val wsClient = WSProbe()
//    WS
//    WS("/blogentry/push",wsClient.flow) ~>
//    a.flatMap(_.unbind())
  }

}

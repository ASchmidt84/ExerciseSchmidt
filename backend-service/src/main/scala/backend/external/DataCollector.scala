package backend.external

import akka.Done
import org.jsoup.Jsoup
import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.time.{LocalDate, LocalDateTime, ZonedDateTime}
import java.util.TimeZone
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Externer Datensammler
 */
object DataCollector {

  implicit private val jsonReader: Reads[JsonData] = (
    (JsPath \ "id").read[Int] and
      (JsPath \ "date_gmt").read[String].map{str =>
        LocalDateTime.parse(str).atZone(TimeZone.getTimeZone("GMT").toZoneId)
      } and
      (JsPath \ "title" \ "rendered").read[String] and
      (JsPath \ "content" \ "rendered").read[String]
  )(JsonData.apply _)

  /**
   * Sammelt alle BlogEinträge und liefert diese Zurück.
   * Hier keine Seiteneffekte
   * @param url Ziel, von wo abgerufen werden soll
   * @param ec
   * @return Liefert eine leere Liste bei Fehlern oder wenn nichts da ist.
   *         Liste enthält alle Informationen, die nötig sind zu Erstellung
   */
  def callBlogEntries(url: String)(implicit ec: ExecutionContext): Future[Seq[JsonData]] = {
    val call = Future{
      Jsoup.connect(url)
        .followRedirects(true)
        .userAgent("Mozilla")
        .ignoreContentType(true)
        .maxBodySize(20000000)
        .timeout(60*1000)
        .execute().body()
    }

    for{
      json <- call.map(k => Try(Json.parse(k).as[Seq[JsonData]]).toOption).recover(_ => None)
    } yield {
      json.getOrElse(Nil)
    }

  }.recover(_ => Nil)

}

/**
 * ADT für einfacheres Arbeiten und weniger JSON Verarbeitungen!
 * @param id ID
 * @param dateGmt Datum im GMT Format
 * @param title Titel des Blogeintrages
 * @param content Inhalt (HTML) des Beitrages
 */
sealed case class JsonData(id: Int,
                           dateGmt: ZonedDateTime,
                           title: String,
                           content: String)
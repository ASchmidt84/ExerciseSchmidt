package backend.projection

import akka.actor.typed.ActorSystem
import backend.entity.BlogEntry
import backend.external.JsonData

import scala.concurrent.Future

/**
 * Erstellt neue Entitäten von gelieferten Inhalten
 * @param system
 */
class BlogEntryGeneratorHandler()(protected implicit val system: ActorSystem[_]) extends EntityAccess {

  /**
   * Führt ein create auf eine neue Entity aus
   * @param data
   * @return
   */
  def insertBlogEntry(data: JsonData): Future[BlogEntry.Summary] = {
    entity(data.id.toString)
      .askWithStatus(reply => BlogEntry.Create(data.id,data.title,data.dateGmt,data.content,reply))
  }


}

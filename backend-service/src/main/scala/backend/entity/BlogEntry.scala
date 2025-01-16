package backend.entity

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import akka.serialization.jackson.CborSerializable
import org.jsoup.Jsoup

import java.time.ZonedDateTime
import scala.annotation.tailrec
import scala.concurrent.duration.DurationInt

object BlogEntry {

  type ReplyActor = ActorRef[StatusReply[Summary]]
  type ReplyEvent = ReplyEffect[Event,State]

  // --------- COMMAND ---------------------------------------------------
  /**
   * The current state held by the `EventSourcedBehavior`.
   */
  sealed trait Command extends CborSerializable {
    def replyTo: ReplyActor
  }

  final case class Create(id: Long,
                          title: String,
                          dateGmt: ZonedDateTime,
                          blogEntryText: String,
                          replyTo: ReplyActor) extends Command

  final case class Get(replyTo: ReplyActor) extends Command

  // --------- SUMMARY ---------------------------------------------------

  final case class Summary(id: Long,
                           title: String,
                           entryCreateDate: ZonedDateTime,
                           blogEntryText: String) extends CborSerializable {

    /**
     * Gesamter Inhalt des BlogPosts aber ohne HTML
     */
    lazy val textWithoutHtml: String = {
      Jsoup.parse(blogEntryText)
        .body()
        .wholeText()
    }

    /**
     * Gezählte Wort Map mit Scala Boardmitteln
     */
    lazy val countedMapScala: Map[String, Int] = {
      wordList
        .groupBy(identity)
        .map(g => g._1 -> g._2.length)
    }

    /**
     * Gezählte Wort Map mit Recursiver function
     */
    lazy val countedMapRecursive: Map[String,Int] = countRecursive(wordList)

    /**
     * Wortliste in lowerCase und bereinigt von Satzzeichen
     */
    lazy val wordList: Seq[String] = textWithoutHtml
      .split("\\W")
      .filter(_.trim.nonEmpty)
      .map(_.replaceAll("\\W","").toLowerCase.trim)

    /**
     * Rekursive Methode zum Zählen!
     * @param inputSeq Sequence mit allen Wörtern aber kleingeschrieben!!!
     * @param countedMap Ergebnis Map
     * @return
     */
      @tailrec
    private def countRecursive(inputSeq: Seq[String],
                               countedMap: Map[String,Int] = Map.empty): Map[String,Int] = {
      inputSeq.headOption match {
        case None => countedMap
        case Some(word) if countedMap.contains(word) =>
          countRecursive(inputSeq.tail, countedMap.filterNot(_._1 == word) + (word -> (countedMap(word)+1) ) )
        case Some(word) =>
          countRecursive(inputSeq.tail, countedMap + (word -> 1))
      }
    }

  }

  // --------- EVENT ------------------------------------------------------

  sealed trait Event extends CborSerializable {
    def eventTime: ZonedDateTime
    def entityId: String
  }

  final case class Created(id: Long,
                           title: String,
                           entryCreateDate: ZonedDateTime,
                           text: String,
                           entityId: String,
                           eventTime: ZonedDateTime = ZonedDateTime.now) extends Event

  // --------- STATE ------------------------------------------------------

  final case class State(id: Option[Long],
                         title: Option[String],
                         entryCreateDate: Option[ZonedDateTime],
                         blogEntryText: Option[String]) extends CborSerializable {

    implicit def toSummary: Summary = Summary(
      id.get,title.get,entryCreateDate.get,blogEntryText.get
    )

    def isEmpty: Boolean = id.isEmpty || title.isEmpty || entryCreateDate.isEmpty || blogEntryText.isEmpty

  }

  object State {
    val empty = State(
      None,None,None,None
    )
  }

  // --------- HANDLER ----------------------------------------------------

  /**
   * Behandelt alle commands und verteilt diese dann an dezidierte Funktionen
   * @param entityId Id der Entität
   * @param state Aktuell State der Entität
   * @param command Das Command das angewendet werden soll
   * @return
   */
  private def handleCommand(entityId: String,
                            state: State,
                            command: Command): ReplyEvent  = command match {
    case g if state.isEmpty => handleCreateCmd(entityId,state,g)

    case Get(reply) =>
      Effect.reply(reply)(StatusReply.Success(state.toSummary))

    case g => Effect.reply(g.replyTo)(StatusReply.Error(s"Sorry command ${g.getClass.getSimpleName} has no action defined."))
  }


  /**
   * Behandelt nur den Erstellungsprozess. Entität muss leer sein!
   * @param entityId Id der Entität
   * @param state Aktuell State der Entität
   * @param cmd Das Command das angewendet werden soll
   * @return
   */
  private def handleCreateCmd(entityId: String,
                              state: State,
                              cmd: Command): ReplyEvent = cmd match {
    case Create(id,title,date,txt,reply) =>
      Effect
        .persist(Created(id,title.trim,date,txt.trim,entityId))
        .thenReply(reply)(t => StatusReply.Success(t.toSummary))

    case g => Effect.reply(g.replyTo)(StatusReply.Error(s"Sorry command ${g.getClass.getSimpleName} has no action defined in creation process"))
  }


  // ---- Event Handler -------

  /**
   * Ändert den Status aufgrund von Events
   * @param state Aktuelle State
   * @param event Das Event das eine Mutation durchführt
   * @return
   */
  private def handleEvent(state: State,
                          event: Event): State = event match {
    case Created(id,title,date,txt,_,_) =>
      state.copy(
        id = Some(id), title = Some(title),
        entryCreateDate = Some(date), blogEntryText = Some(txt)
      )

    case _ => state
  }



  // --------- INIT ---------------------------------------------------------

  /**
   * The current state held by the `EventSourcedBehavior`.
   */
  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("BlogEntry")


  def init(system: ActorSystem[_]): Unit = {
    ClusterSharding(system).init(
      Entity(EntityKey)(entityContext => BlogEntry(entityContext.entityId) )
    )
  }

  def apply(cartId: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command,Event,State](
        persistenceId = PersistenceId(EntityKey.name,cartId),
        emptyState = State.empty,
        commandHandler = (state,command) => handleCommand(cartId, state, command),
        eventHandler = (state,event) => handleEvent(state,event)
      )
      .withRetention(
        RetentionCriteria.snapshotEvery(numberOfEvents = 400, keepNSnapshots = 100)
      )
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
  }

}

package backend.projection

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.query.Offset
import akka.persistence.query.typed.EventEnvelope
import akka.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
import akka.projection.{Projection, ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.r2dbc.scaladsl.R2dbcProjection
import akka.projection.scaladsl.SourceProvider
import backend.entity.BlogEntry
import com.typesafe.config.Config

object BlogEntryProjection {

  def init(system: ActorSystem[_],
           config: Config): Unit = {

    val targetSendingUrl = config.getString("target.url")

    def sourceProvider(sliceRange: Range): SourceProvider[Offset, EventEnvelope[BlogEntry.Event]] = {
      EventSourcedProvider
        .eventsBySlices[BlogEntry.Event](
          system, readJournalPluginId = R2dbcReadJournal.Identifier,
          "BlockEntry",
          sliceRange.min, sliceRange.max
        )
    }

    def projection(sliceRange: Range): Projection[EventEnvelope[BlogEntry.Event]] = {
      val minSlice = sliceRange.min
      val maxSlice = sliceRange.max
      val projectionId = ProjectionId("BlockEntryProjection",s"block-entry-$minSlice-$maxSlice")

      R2dbcProjection.exactlyOnce(
        projectionId,
        settings = None,
        sourceProvider(sliceRange),
        handler = () =>
          new BlogEntryProjectionHandler(
            s"block-entry-$minSlice-$maxSlice", system, targetSendingUrl
          )
      )(system)
    }

    ShardedDaemonProcess(system)
      .initWithContext(
        name = "BlockEntryProjection",
        initialNumberOfInstances = 4,
        behaviorFactory = {daemonCtx =>
          val sliceRanges = EventSourcedProvider.sliceRanges(
            system, R2dbcReadJournal.Identifier, daemonCtx.totalProcesses
          )
          val sliceRange = sliceRanges(daemonCtx.processNumber)
          ProjectionBehavior(projection(sliceRange))
        },
        ShardedDaemonProcessSettings(system),
        stopMessage = ProjectionBehavior.Stop
      )

  }

}

import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.UnpersistentBehavior
import backend.entity.BlogEntry
import backend.entity.BlogEntry.Summary
import backend.external.DataCollector
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike

class ExternalTest extends AsyncFlatSpec {

  it should "not be empty" in {
    DataCollector.callBlogEntries("https://thekey.academy/wp-json/wp/v2/posts?order=desc") map { seq =>
      assert(seq.nonEmpty)
    }
  }

}

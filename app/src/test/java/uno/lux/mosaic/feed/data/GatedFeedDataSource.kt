package uno.lux.mosaic.feed.data

import kotlinx.coroutines.CompletableDeferred

/**
 * A [FeedDataSource] whose every fetch waits until the test answers it, so a test can hold one
 * request on the wire while another lands. [calls] records each fetch in the order it was made.
 */
internal class GatedFeedDataSource : FeedDataSource {

    class Call(
        val cursor: String?,
    ) {
        val answer = CompletableDeferred<FeedPage>()
    }

    val calls = mutableListOf<Call>()

    override suspend fun fetch(cursor: String?): FeedPage {
        val call = Call(cursor)
        calls += call

        return call.answer.await()
    }
}

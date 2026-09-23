package uno.lux.mosaic.feed.data

import kotlinx.coroutines.CompletableDeferred

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

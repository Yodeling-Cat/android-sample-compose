package uno.lux.mosaic.feed.data.network

import uno.lux.mosaic.common.data.network.emptyPage

class FakeFeedApi(
    private val feedResponse: FeedResponse = FeedResponse(data = emptyList(), page = emptyPage),
    /** Overrides [feedResponse] for specific cursor values, allowing multi-page feed tests. */
    private val feedResponseByCursor: Map<String?, FeedResponse> = emptyMap(),
) : FeedApi {

    override suspend fun getFeed(cursor: String?, limit: Int, include: String): FeedResponse =
        feedResponseByCursor[cursor] ?: feedResponse
}

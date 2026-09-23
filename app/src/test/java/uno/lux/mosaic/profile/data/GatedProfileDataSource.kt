package uno.lux.mosaic.profile.data

import kotlinx.coroutines.CompletableDeferred
import uno.lux.mosaic.user.data.domain.UserId

internal class GatedProfileDataSource : ProfileDataSource {

    class Call<T>(
        val userId: UserId,
        val cursor: String?,
    ) {
        val answer = CompletableDeferred<T>()
    }

    val refreshCalls = mutableListOf<Call<ProfileRefreshData>>()
    val morePostsCalls = mutableListOf<Call<PostsPage>>()
    val bookmarkCalls = mutableListOf<Call<PostsPage>>()
    val likeCalls = mutableListOf<Call<PostsPage>>()

    override suspend fun refresh(userId: UserId) = await(refreshCalls, userId, cursor = null)

    override suspend fun loadMorePosts(userId: UserId, cursor: String?) = await(morePostsCalls, userId, cursor)

    override suspend fun bookmarks(userId: UserId, cursor: String?) = await(bookmarkCalls, userId, cursor)

    override suspend fun likes(userId: UserId, cursor: String?) = await(likeCalls, userId, cursor)

    private suspend fun <T> await(
        calls: MutableList<Call<T>>,
        userId: UserId,
        cursor: String?,
    ): T {
        val call = Call<T>(userId, cursor)
        calls += call

        return call.answer.await()
    }
}

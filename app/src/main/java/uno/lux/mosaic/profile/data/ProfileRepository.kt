package uno.lux.mosaic.profile.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostKey
import uno.lux.mosaic.post.data.domain.key
import uno.lux.mosaic.profile.data.domain.Profile
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.UserId

/** [ids] emits `null` until the first load lands, which tells an unopened tab from an empty one. */
interface PostList {
    val ids: Flow<List<PostId>?>

    val hasMore: Flow<Boolean>

    suspend fun ensureLoaded()

    suspend fun refreshIfLoaded()

    suspend fun loadMore()
}

class ProfileRepository(
    private val dataSource: ProfileDataSource,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val currentUserId: UserId,
) {
    private val _profiles = MutableStateFlow<Map<UserId, Profile>>(emptyMap())
    private val _userPostIds = MutableStateFlow<Map<UserId, List<PostId>>>(emptyMap())

    private data class PageState(
        val cursor: String?,
        val hasMore: Boolean,
    )

    private data class TabState(
        val ids: List<PostId>,
        val cursor: String?,
        val hasMore: Boolean,
        val oldestLoaded: PostKey?,
    )

    private val _postPage = MutableStateFlow<Map<UserId, PageState>>(emptyMap())

    // The two on-demand tabs differ only in which endpoint fills them and which flag keeps a post
    // on the list, so the ordering and paging bookkeeping lives in one place.
    private val savedPosts = OnDemandPostIds(dataSource::bookmarks, Post::isBookmarked)
    private val likedPosts = OnDemandPostIds(dataSource::likes, Post::isLiked)

    fun profile(userId: UserId): Flow<Profile> =
        _profiles.map { it[userId] ?: emptyProfile(userId) }

    fun postIds(userId: UserId): Flow<List<PostId>> =
        _userPostIds.map { it[userId] ?: emptyList() }

    fun hasMorePosts(userId: UserId): Flow<Boolean> =
        _postPage.map { it[userId]?.hasMore ?: false }

    fun saved(userId: UserId): PostList = savedPosts.forUser(userId)

    fun liked(userId: UserId): PostList = likedPosts.forUser(userId)

    suspend fun refresh(userId: UserId) {
        val data = dataSource.refresh(userId)
        val page = ingest(data.page)

        _postPage.update { it + (userId to PageState(page.cursor, page.hasMore)) }

        _userPostIds.update { it + (userId to page.posts.map { p -> p.id }) }
        _profiles.update {
            it + (userId to Profile(userId = userId, postsCount = data.postsCount))
        }
    }

    /**
     * A page whose cursor no longer matches (a refresh landed meanwhile) is dropped, so it cannot
     * repeat IDs.
     */
    suspend fun loadMorePosts(userId: UserId) {
        val page = _postPage.value[userId] ?: return
        if (!page.hasMore) return

        val result = ingest(dataSource.loadMorePosts(userId, page.cursor))
        if (_postPage.value[userId]?.cursor != page.cursor) return

        _postPage.update { it + (userId to PageState(result.cursor, result.hasMore)) }
        _userPostIds.update { map ->
            val existing = map[userId] ?: emptyList()
            map + (userId to existing + result.posts.map { it.id })
        }
    }

    private fun emptyProfile(userId: UserId) = Profile(userId = userId, postsCount = 0)

    private fun ingest(page: PostsPage): PostsPage {
        postRepository.ingest(page.posts)
        userRepository.ingest(page.users)

        return page
    }

    private inner class OnDemandPostIds(
        private val fetchPage: suspend (UserId, String?) -> PostsPage,
        private val stillBelongs: (Post) -> Boolean,
    ) {
        private val _state = MutableStateFlow<Map<UserId, TabState>>(emptyMap())

        fun forUser(userId: UserId): PostList = Tab(userId)

        private inner class Tab(
            private val userId: UserId,
        ) : PostList {

            /**
             * Your own lists derive from the post flags. Another user's are echoed as fetched,
             * because `isLiked` and `isBookmarked` describe the viewer.
             */
            override val ids: Flow<List<PostId>?> =
                if (userId == currentUserId) derivedIds() else echoedIds()

            override val hasMore: Flow<Boolean> = _state.map { it[userId]?.hasMore ?: false }

            private val hasLoaded: Boolean
                get() = _state.value.containsKey(userId)

            /**
             * A post older than [TabState.oldestLoaded] is held back: it belongs to a page not yet
             * fetched.
             */
            private fun derivedIds(): Flow<List<PostId>?> =
                combine(_state, postRepository.entities) { states, entities ->
                    val state = states[userId] ?: return@combine null
                    val floor = state.oldestLoaded.takeIf { state.hasMore }

                    entities.values
                        .filter(stillBelongs)
                        .map { it.key }
                        .filter { floor == null || it >= floor }
                        .sortedDescending()
                        .map { it.id }
                }.distinctUntilChanged()

            private fun echoedIds(): Flow<List<PostId>?> =
                _state.map { it[userId]?.ids }.distinctUntilChanged()

            override suspend fun ensureLoaded() {
                if (hasLoaded) return

                refresh()
            }

            override suspend fun refreshIfLoaded() {
                if (!hasLoaded) return

                refresh()
            }

            private suspend fun refresh() {
                val page = ingest(fetchPage(userId, null))

                _state.update {
                    it + (
                        userId to TabState(
                            ids = page.posts.map { post -> post.id },
                            cursor = page.cursor,
                            hasMore = page.hasMore,
                            oldestLoaded = page.posts.lastOrNull()?.key,
                        )
                    )
                }
            }

            /**
             * A page whose cursor no longer matches (a refresh restarted the tab meanwhile) is
             * dropped.
             */
            override suspend fun loadMore() {
                val current = _state.value[userId] ?: return
                if (!current.hasMore) return

                val page = ingest(fetchPage(userId, current.cursor))

                _state.update { states ->
                    val latest = states[userId]
                    if (latest == null || latest.cursor != current.cursor) return@update states

                    states + (
                        userId to TabState(
                            ids = latest.ids + page.posts.map { post -> post.id },
                            cursor = page.cursor,
                            hasMore = page.hasMore,
                            // An empty page moves the cursor but not the floor.
                            oldestLoaded = page.posts.lastOrNull()?.key ?: latest.oldestLoaded,
                        )
                    )
                }
            }
        }
    }
}

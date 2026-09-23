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

/**
 * One on-demand profile tab — Saved or Likes — for one user, handed out with that user already
 * bound, so a caller cannot pair one tab's list with another tab's `hasMore`.
 *
 * [ids] emits `null` until the tab's first load lands, which is how a caller tells an empty tab
 * from an unopened one.
 */
interface PostList {
    val ids: Flow<List<PostId>?>

    val hasMore: Flow<Boolean>

    /** Loads this list if nothing has yet, so a tab reopened later is not fetched again. */
    suspend fun ensureLoaded()

    /**
     * Re-fetches this list, but only if it was ever opened: a refresh must not reach for a tab
     * nobody asked to see, least of all the private one.
     */
    suspend fun refreshIfLoaded()

    suspend fun loadMore()
}

/**
 * Source of truth for a user's profile and the ordered IDs of their posts, saved posts and liked
 * posts. Callers resolve the IDs through [PostRepository.entities].
 *
 * Every page carries its posts' authors into [UserRepository], so a profile opened cold can draw
 * its rows without waiting on `GET /users/:id`.
 *
 * For [currentUserId], [saved] and [liked] are derived from each post's flag rather than echoed
 * from the fetch, so membership follows a like or bookmark made anywhere. Another user's lists are
 * echoed as fetched, because `isLiked` and `isBookmarked` describe the viewer, not the profile's
 * owner.
 */
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

    /**
     * One on-demand tab's loaded window for one user: the IDs the server sent, where to continue
     * from, and [oldestLoaded], the floor of what has been paged in. Absent until the first load.
     */
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

    /** The posts [userId] saved — private to its owner, and loaded only once its tab is opened. */
    fun saved(userId: UserId): PostList = savedPosts.forUser(userId)

    /** The posts [userId] liked. Public, but loaded on the same on-demand terms as [saved]. */
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
     * Appends the next page of [userId]'s posts — unless a [refresh] landed while it was on the
     * wire. That restarted the list, which shows as a cursor that no longer matches, and the page
     * is dropped rather than glued on, where it could repeat an ID the refreshed page holds.
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

    /** Puts a page's posts and their authors into the shared stores, and hands the page back. */
    private fun ingest(page: PostsPage): PostsPage {
        postRepository.ingest(page.posts)
        userRepository.ingest(page.users)

        return page
    }

    /**
     * One on-demand tab's post IDs per user, filled by [fetchPage]. Its posts and authors go into
     * the shared stores on the way through, so a like made anywhere reaches these lists.
     */
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
             * Which shape this tab takes is fixed by whose profile it is, so the choice is made
             * once rather than on every emission — an echoed list never watches the entity store.
             */
            override val ids: Flow<List<PostId>?> =
                if (userId == currentUserId) derivedIds() else echoedIds()

            override val hasMore: Flow<Boolean> = _state.map { it[userId]?.hasMore ?: false }

            /**
             * Whether this tab was ever opened. Private, because a caller reading it would be
             * deciding what to ask for from a snapshot that can move before the request goes out.
             */
            private val hasLoaded: Boolean
                get() = _state.value.containsKey(userId)

            /**
             * Every post [stillBelongs] accepts, in the server's `(createdAt, id)` order. A post
             * older than [TabState.oldestLoaded] is on a page not yet sent, so it is held back
             * rather than placed at the end of a partial list.
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

            /**
             * Someone else's list can only be echoed: [stillBelongs] reads a viewer-scoped flag,
             * which on their profile describes *you*, so it says nothing about what belongs there.
             */
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
             * Appends the next page — unless a refresh landed while it was on the wire, which
             * restarted the tab and shows as a cursor that no longer matches. Such a page is
             * dropped, the way [loadMorePosts] drops one.
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

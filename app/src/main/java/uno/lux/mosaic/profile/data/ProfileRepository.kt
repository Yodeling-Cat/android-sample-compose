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
 * Source of truth for a user's profile metadata and the ordered IDs of their posts.
 *
 * [profile] streams the counts; [postIds] the IDs of the posts that user authored, which callers
 * resolve through [PostRepository.entities], so likes and bookmarks are reflected with no
 * involvement from here. Post mutations go directly through [PostRepository]. [hasMorePosts]
 * signals whether another page exists; [loadMorePosts] appends it.
 *
 * Every page carries its posts' authors into [UserRepository], which is what lets a profile
 * opened cold draw its rows off the posts page alone, rather than waiting on `GET /users/:id`.
 *
 * [saved] and [liked] are the same arrangement for the posts a user saved and liked, each handed
 * out as a whole [PostList] rather than as a spread of per-tab methods. Unlike the Posts tab,
 * each is loaded on demand ([PostList.ensureLoaded]) rather than by [refresh], and emits `null`
 * until its first load lands.
 *
 * For the signed-in user those two lists are **derived from the flag, not echoed from the
 * fetch**, so membership moves both ways and from anywhere with no re-fetch. Another user's
 * Likes tab is echoed exactly as fetched, because `isLiked`/`isBookmarked` are viewer-scoped —
 * on their profile the flags describe you, not them. Hence [currentUserId].
 *
 * Where the data comes from is decided by [ProfileDataSource].
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

    suspend fun loadMorePosts(userId: UserId) {
        val page = _postPage.value[userId] ?: return
        if (!page.hasMore) return

        val result = ingest(dataSource.loadMorePosts(userId, page.cursor))

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
     * One profile tab's worth of post IDs, per user, filled on demand by [fetchPage] and paged
     * with its cursor. [forUser] narrows it to the one list a caller is asking about.
     *
     * The posts and their authors go into the shared entity stores on the way through, which is
     * what lets a like toggled anywhere reach these lists.
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
             * Your own list is *derived*, not echoed: every post [stillBelongs] accepts, in the
             * server's own `(createdAt, id)` order. So liking a post anywhere inserts it here in
             * the right place, and unliking removes it, with no re-fetch.
             *
             * A post older than [TabState.oldestLoaded] belongs to a page the server hasn't sent
             * yet, so it is held back rather than jumped to the end of a partial list. A
             * fully-loaded list has no floor.
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

            override suspend fun loadMore() {
                val current = _state.value[userId] ?: return
                if (!current.hasMore) return

                val page = ingest(fetchPage(userId, current.cursor))

                _state.update {
                    it + (
                        userId to TabState(
                            ids = current.ids + page.posts.map { post -> post.id },
                            cursor = page.cursor,
                            hasMore = page.hasMore,
                            // An empty page moves the cursor but not the floor.
                            oldestLoaded = page.posts.lastOrNull()?.key ?: current.oldestLoaded,
                        )
                    )
                }
            }
        }
    }
}

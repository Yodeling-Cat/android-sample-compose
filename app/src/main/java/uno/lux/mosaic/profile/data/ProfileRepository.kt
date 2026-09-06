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
 * bound. Binding it is the point: the IDs, the paging state and the fetches of a single tab arrive
 * as one thing, so a caller cannot pair one tab's list with another tab's `hasMore`.
 *
 * [ids] emits `null` until the tab's first load lands, which is how a caller tells an empty tab
 * from an unopened one.
 */
interface PostList {
    val ids: Flow<List<PostId>?>

    val hasMore: Flow<Boolean>

    /**
     * Loads this list if nothing has yet, and does nothing at all otherwise — a tab reopened
     * later is served from what is already here rather than fetched again.
     */
    suspend fun ensureLoaded()

    /**
     * Re-fetches this list, but only if it was ever opened: a profile refresh must not reach for
     * a tab nobody asked to see, least of all the private one.
     */
    suspend fun refreshIfLoaded()

    suspend fun loadMore()
}

/**
 * Source of truth for a user's profile metadata and the ordered IDs of their posts.
 *
 * [profile] streams the counts for a given user. [postIds] streams the ordered list of post IDs
 * authored by that user; callers resolve them via [PostRepository.entities] so mutations (likes,
 * bookmarks) are reflected without any involvement from this repository. Post mutations go
 * directly through [PostRepository] — not through here.
 *
 * [hasMorePosts] signals whether another page exists; [loadMorePosts] appends it into the flows
 * above.
 *
 * Every page of every list carries its posts' authors, and all three put them into
 * [UserRepository] the way the feed does. On the Posts tab that is only ever the profile's own
 * user, and sideloading it is what lets a profile opened cold draw its rows off the posts page
 * alone, rather than waiting on `GET /users/:id` to come back as well.
 *
 * [saved] and [liked] are the same arrangement for the posts a user saved and liked, each handed
 * out as a whole [PostList] rather than as a spread of per-tab methods. They differ from the Posts
 * tab in two ways: each is loaded on demand ([PostList.ensureLoaded]) rather than by [refresh] — a
 * tab nobody opened costs no request, and the Saved one is private to its owner besides — and each
 * emits `null` until its first load lands. Their authors are arbitrary users the caller may never
 * have met, which is what makes the sideload indispensable there rather than merely useful.
 *
 * For the signed-in user those two lists are **derived from the flag, not echoed from the fetch**:
 * a list defined by `isBookmarked`/`isLiked` *is* the set of entities carrying that flag, ordered
 * by the server's own `(createdAt, id)` keyset. Membership therefore moves both ways and from
 * anywhere — liking a post in the Posts tab or the feed inserts it in the Likes tab in the right
 * place, unliking removes it — with no re-fetch and no dependence on whether the post happened to
 * be in a page the server already sent. Paging is the only limit: a post below the loaded window's
 * floor is held back until the page it belongs to arrives, so it can't jump the queue.
 *
 * None of that applies to *another* user's Likes tab, which is echoed exactly as fetched:
 * `isLiked`/`isBookmarked` are viewer-scoped — on their profile the flags describe you, not them —
 * so the fetched order is the only thing that says what belongs there. Hence [currentUserId].
 *
 * Where the data comes from — in-memory sample data vs. a live network call — is decided by
 * [ProfileDataSource].
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
     * from, and [oldestLoaded] — the key of the last post delivered, i.e. the floor of what has
     * been paged in. Absent until that tab's first load lands.
     */
    private data class TabState(
        val ids: List<PostId>,
        val cursor: String?,
        val hasMore: Boolean,
        val oldestLoaded: PostKey?,
    )

    private val _postPage = MutableStateFlow<Map<UserId, PageState>>(emptyMap())

    // The two on-demand tabs. They differ only in which endpoint fills them and which flag keeps
    // a post on the list — the ordering, paging and "not asked yet" bookkeeping is identical, so
    // it lives in one place.
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
     * with its cursor. State for every user this tab has been opened for lives here; [forUser]
     * narrows it to the one list a caller is asking about.
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
             * Which of the two shapes this tab takes is fixed by whose profile it is, so the
             * choice is made once here rather than on every emission — an echoed list has no
             * reason to watch the entity store at all.
             */
            override val ids: Flow<List<PostId>?> =
                if (userId == currentUserId) derivedIds() else echoedIds()

            override val hasMore: Flow<Boolean> = _state.map { it[userId]?.hasMore ?: false }

            /**
             * Whether this tab was ever opened. Private, because the two decisions taken from it
             * — load once, and re-fetch only what was opened — are this list's own to make; a
             * caller reading it would be deciding what to ask for from a snapshot that can move
             * before the request goes out.
             */
            private val hasLoaded: Boolean
                get() = _state.value.containsKey(userId)

            /**
             * Your own list is *derived*, not echoed: every post [stillBelongs] accepts, in the
             * server's own `(createdAt, id)` order. So liking a post anywhere — the Posts tab, the
             * feed, a post's detail screen — inserts it here in the right place, and unliking
             * removes it, with no re-fetch and no asymmetry between the two directions.
             *
             * The one thing paging costs: a post older than [TabState.oldestLoaded] belongs to a
             * page the server hasn't sent yet, so it is held back rather than jumped to the end of
             * a partial list. It arrives in order once that page is paged in. A fully-loaded list
             * has no floor.
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
             * Someone else's list can only be echoed. [stillBelongs] reads a viewer-scoped flag,
             * which on their profile describes *you*, not them — it says nothing about what
             * belongs there.
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

package uno.lux.mosaic.feed.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.user.data.UserRepository

/**
 * Pagination state for the home feed. Starts as [NotLoaded] until the first [FeedRepository.refresh]
 * completes; transitions to [Loaded] atomically with the ingested post and user data, so the ViewModel's
 * combine never sees an inconsistent "loaded flag but no entities yet" intermediate state.
 */
sealed interface FeedState {
    data object NotLoaded : FeedState

    data class Loaded(
        val postIds: List<PostId>,
        val hasMore: Boolean,
    ) : FeedState
}

/**
 * Source of truth for the home feed's ordered post IDs.
 *
 * [feedState] carries the IDs in display order plus the pagination flag; consumers resolve IDs to
 * [uno.lux.mosaic.post.data.domain.Post] objects via [uno.lux.mosaic.post.data.PostRepository.entities] so mutations (likes, bookmarks) propagate automatically
 * without re-fetching. [refresh] re-fetches the first page. [loadMore] appends the next page.
 * [publish] creates a post and puts it at the head of the feed.
 *
 * Where the data comes from — in-memory sample data vs. a live network call — is decided by
 * [FeedDataSource]. This class owns only the ordered ID list and pagination state.
 */
class FeedRepository(
    private val dataSource: FeedDataSource,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
) {
    private val _feedState = MutableStateFlow<FeedState>(FeedState.NotLoaded)
    val feedState: StateFlow<FeedState> = _feedState.asStateFlow()

    private var nextCursor: String? = null

    fun reset() {
        _feedState.value = FeedState.NotLoaded
    }

    suspend fun refresh() {
        val page = dataSource.fetch(cursor = null)
        postRepository.ingest(page.posts)
        userRepository.ingest(page.users)
        nextCursor = page.nextCursor
        // Set last: entities and users are already ingested, so when this emits the combine
        // can resolve every post ID without a stale-data gap.
        _feedState.value = FeedState.Loaded(page.posts.map { it.id }, page.hasMore)
    }

    /**
     * Publishes [draft] and returns the new post's ID. [PostRepository] stores the post and its
     * embedded author; the ID is then prepended to the feed so the composer's result is visible
     * at the top without a round trip. A feed that hasn't loaded yet is left alone — its first
     * fetch will carry the new post anyway.
     */
    suspend fun publish(draft: NewPost): PostId {
        val created = postRepository.create(draft)

        _feedState.update { state ->
            if (state is FeedState.Loaded) {
                state.copy(postIds = listOf(created.id) + state.postIds)
            } else {
                state
            }
        }

        return created.id
    }

    suspend fun loadMore() {
        val current = _feedState.value as? FeedState.Loaded ?: return
        if (!current.hasMore) return
        val page = dataSource.fetch(cursor = nextCursor)
        postRepository.ingest(page.posts)
        userRepository.ingest(page.users)
        nextCursor = page.nextCursor
        _feedState.value = FeedState.Loaded(
            postIds = buildList {
                addAll(current.postIds)
                page.posts.mapTo(this) { it.id }
            },
            hasMore = page.hasMore,
        )
    }
}

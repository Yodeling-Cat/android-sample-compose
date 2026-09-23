package uno.lux.mosaic.feed.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.user.data.UserRepository

sealed interface FeedState {
    data object NotLoaded : FeedState

    data class Loaded(
        val postIds: List<PostId>,
        val hasMore: Boolean,
        val nextCursor: String?,
    ) : FeedState
}

class FeedRepository(
    private val dataSource: FeedDataSource,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
) {
    private val _feedState = MutableStateFlow<FeedState>(FeedState.NotLoaded)
    val feedState: StateFlow<FeedState> = _feedState.asStateFlow()

    fun reset() {
        _feedState.value = FeedState.NotLoaded
    }

    suspend fun refresh() {
        val page = dataSource.fetch(cursor = null)
        postRepository.ingest(page.posts)
        userRepository.ingest(page.users)

        _feedState.value = FeedState.Loaded(
            postIds = page.posts.map { it.id },
            hasMore = page.hasMore,
            nextCursor = page.nextCursor,
        )
    }

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

    /**
     * A page whose cursor no longer matches (a refresh landed meanwhile) is dropped, so it cannot
     * repeat IDs.
     */
    suspend fun loadMore() {
        val current = _feedState.value as? FeedState.Loaded ?: return
        if (!current.hasMore) return

        val cursor = current.nextCursor
        val page = dataSource.fetch(cursor = cursor)
        postRepository.ingest(page.posts)
        userRepository.ingest(page.users)

        _feedState.update { state ->
            if (state !is FeedState.Loaded || state.nextCursor != cursor) return@update state

            state.copy(
                postIds = state.postIds + page.posts.map { it.id },
                hasMore = page.hasMore,
                nextCursor = page.nextCursor,
            )
        }
    }
}

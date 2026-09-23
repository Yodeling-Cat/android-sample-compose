package uno.lux.mosaic.post.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostWithUsers
import uno.lux.mosaic.user.data.UserRepository

class PostRepository(
    private val dataSource: PostDataSource,
    private val userRepository: UserRepository,
) {
    private val _entities = MutableStateFlow<Map<PostId, Post>>(emptyMap())
    val entities: StateFlow<Map<PostId, Post>> = _entities.asStateFlow()

    private val _deletedIds = MutableStateFlow<Set<PostId>>(emptySet())

    /** Absence from [entities] can't tell "never fetched" from "deleted"; this can. */
    val deletedIds: StateFlow<Set<PostId>> = _deletedIds.asStateFlow()

    fun ingest(posts: List<Post>) {
        _entities.update { current -> current + posts.associateBy { it.id } }
    }

    /** Null when the server says the post does not exist. */
    suspend fun load(postId: PostId): Post? {
        // The store already knows this one is gone, and must not resurrect it into [entities]
        // whoever asks. No caller can reach this today; it guards the invariant, not a screen.
        if (postId in _deletedIds.value) return null

        return dataSource.fetch(postId)?.let(::store)
    }

    suspend fun create(draft: NewPost): Post = store(dataSource.create(draft))

    private fun store(fetched: PostWithUsers): Post {
        _entities.update { it + (fetched.post.id to fetched.post) }
        userRepository.ingest(fetched.users)

        return fetched.post
    }

    suspend fun delete(postId: PostId) {
        dataSource.delete(postId)
        // Marked deleted *before* the entity drops, so no observer ever sees the post absent
        // without [deletedIds] already explaining why.
        _deletedIds.update { it + postId }
        _entities.update { it - postId }
    }

    suspend fun toggleLike(postId: PostId) {
        val before = _entities.value[postId] ?: return
        val liked = !before.isLiked
        val delta = if (liked) 1 else -1
        // A later tap has already moved past this request, so its answer is the one to trust.
        val stillOurs = { post: Post -> post.isLiked == liked }

        updateEntity(postId) {
            it.copy(isLiked = liked, likeCount = it.likeCount + delta)
        }

        try {
            val confirmed = dataSource.setLike(postId, liked)
            updateEntity(postId, stillOurs) {
                it.copy(isLiked = confirmed.isLiked, likeCount = confirmed.likeCount)
            }
        } catch (e: CancellationException) {
            // The screen went away, not the request: it is on the wire and the server most
            // likely took it, so the optimistic value is the better guess to leave in a store
            // that outlives the screen. The next read settles it either way.
            throw e
        } catch (e: Exception) {
            // Takes back this tap's one like from the count as it stands now, not the count
            // [before] held: a refresh that landed meanwhile may have moved it by other people's.
            updateEntity(postId, stillOurs) {
                it.copy(isLiked = before.isLiked, likeCount = it.likeCount - delta)
            }
            throw e
        }
    }

    suspend fun toggleBookmark(postId: PostId) {
        val before = _entities.value[postId] ?: return
        val bookmarked = !before.isBookmarked
        val stillOurs = { post: Post -> post.isBookmarked == bookmarked }

        updateEntity(postId) { it.copy(isBookmarked = bookmarked) }

        try {
            val confirmed = dataSource.setBookmark(postId, bookmarked)
            updateEntity(postId, stillOurs) { it.copy(isBookmarked = confirmed) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateEntity(postId, stillOurs) { it.copy(isBookmarked = before.isBookmarked) }
            throw e
        }
    }

    /**
     * Re-reads the entity inside the update, so fields that landed while a request was in flight
     * survive. [stillOurs] drops an answer a newer tap has overtaken.
     */
    private fun updateEntity(
        postId: PostId,
        stillOurs: (Post) -> Boolean = { true },
        edit: (Post) -> Post,
    ) = _entities.update { entities ->
        val current = entities[postId] ?: return@update entities
        if (!stillOurs(current)) return@update entities

        entities + (postId to edit(current))
    }

    fun commentAdded(postId: PostId) = updateEntity(postId) {
        it.copy(commentCount = it.commentCount + 1)
    }

    suspend fun report(
        postId: PostId,
        reason: ReportReason,
        details: String,
    ) = dataSource.report(postId, reason, details)
}

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

/**
 * Normalized entity store for posts.
 *
 * All screens share one entity map so a like toggled in the feed is immediately visible on a
 * profile and vice versa. [ingest] merges posts fetched by
 * any screen into the shared store; [toggleLike] and [toggleBookmark] mutate individual entries
 * so every observer sees the update in the same emission — optimistically, which having exactly
 * one copy of each post to correct is what makes safe.
 *
 * Ordered, screen-specific lists of post IDs (feed order, per-user order, etc.) live in
 * [uno.lux.mosaic.feed.data.FeedRepository] or [uno.lux.mosaic.profile.data.ProfileRepository]; this store owns only the entity data.
 */
class PostRepository(
    private val dataSource: PostDataSource,
    private val userRepository: UserRepository,
) {
    private val _entities = MutableStateFlow<Map<PostId, Post>>(emptyMap())
    val entities: StateFlow<Map<PostId, Post>> = _entities.asStateFlow()

    private val _deletedIds = MutableStateFlow<Set<PostId>>(emptySet())

    /**
     * IDs this session has deleted. Exists because absence from [entities] is ambiguous — a post
     * can be absent because nothing fetched it *or* because it is gone — and the one observer the
     * ambiguity bites (a detail page open on the post someone deletes from another screen) can't
     * resolve it alone.
     */
    val deletedIds: StateFlow<Set<PostId>> = _deletedIds.asStateFlow()

    fun ingest(posts: List<Post>) {
        _entities.update { current -> current + posts.associateBy { it.id } }
    }

    /**
     * Fetches a single post into the store, returning it — or null when the server says there is
     * no such post, so a caller can render "gone" rather than a retryable failure.
     */
    suspend fun load(postId: PostId): Post? {
        // The store already knows this one is gone, and must not resurrect it into [entities]
        // whoever asks. No caller can reach this today; it guards the invariant, not a screen.
        if (postId in _deletedIds.value) return null

        return dataSource.fetch(postId)?.let(::store)
    }

    /**
     * Publishes [draft] and stores the resulting entity, so any screen already resolving that ID
     * renders it without a re-fetch.
     */
    suspend fun create(draft: NewPost): Post = store(dataSource.create(draft))

    private fun store(fetched: PostWithUsers): Post {
        _entities.update { it + (fetched.post.id to fetched.post) }
        userRepository.ingest(fetched.users)

        return fetched.post
    }

    /**
     * Deletes [postId] and drops it from the store. The ordered ID lists in [uno.lux.mosaic.feed.data.FeedRepository] and
     * [uno.lux.mosaic.profile.data.ProfileRepository] are deliberately left alone: they resolve IDs through [entities], so a
     * removed entity disappears from every screen showing it in the same emission — the same
     * propagation a like toggle relies on, rather than a second place that has to be kept in step.
     */
    suspend fun delete(postId: PostId) {
        dataSource.delete(postId)
        // Marked deleted *before* the entity drops, so no observer ever sees the post absent
        // without [deletedIds] already explaining why.
        _deletedIds.update { it + postId }
        _entities.update { it - postId }
    }

    /** Flips the viewer's like on [postId], optimistically. */
    suspend fun toggleLike(postId: PostId) {
        val before = _entities.value[postId] ?: return
        val liked = !before.isLiked
        // A later tap has already moved past this request, so its answer is the one to trust.
        val stillOurs = { post: Post -> post.isLiked == liked }

        updateEntity(postId) {
            it.copy(isLiked = liked, likeCount = it.likeCount + if (liked) 1 else -1)
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
            updateEntity(postId, stillOurs) {
                it.copy(isLiked = before.isLiked, likeCount = before.likeCount)
            }
            throw e
        }
    }

    /** Flips the viewer's bookmark on [postId], optimistically. */
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
     * Applies [edit] to the stored [postId], if the store still holds it and [stillOurs] accepts
     * what it currently says.
     *
     * Re-reading the entity inside the update is the whole point: an edit describes the fields it
     * owns, so whatever else landed while a request was in flight survives it. [stillOurs] is the
     * other half — an answer that arrives after a newer tap already moved the post describes a
     * state nobody is waiting for any more, and dropping it is what keeps the store on the last
     * thing the user asked for.
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

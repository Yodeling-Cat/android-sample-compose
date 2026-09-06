package uno.lux.mosaic.post.data.domain

import uno.lux.mosaic.album.data.domain.Album
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video
import java.time.Instant

typealias PostId = String

data class Post(
    val id: PostId,
    val url: String,
    val authorId: UserId,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val video: Video? = null,
    val album: Album? = null,
)

/**
 * A single post together with the [users] the server sideloaded beside it — its author, the one
 * user a caller holding nothing but an `authorId` has no other way to resolve. Both halves travel
 * together so [uno.lux.mosaic.post.data.PostRepository] can seed the post and user stores in one
 * step, which is what the two endpoints answering with a single post are for: publishing one, and
 * fetching one the app doesn't hold yet.
 *
 * Carries the sideload verbatim rather than picking the author out of it, exactly as
 * [uno.lux.mosaic.profile.data.PostsPage] does for a page — there is nothing to resolve, and so
 * no way for the resolution to fail on a payload that named an author it did not send.
 */
data class PostWithUsers(
    val post: Post,
    val users: List<User>,
)

/**
 * A post paired with the author it names, both already resolved out of their stores. Distinct
 * from [PostWithUsers], which is what *arrives* — this is what a screen draws, and a screen has
 * nothing to show until it holds the user, not merely a list the user should be somewhere in.
 */
data class PostWithAuthor(
    val post: Post,
    val author: User,
)

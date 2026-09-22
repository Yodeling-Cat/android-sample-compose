package uno.lux.mosaic.post.data

import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostWithUsers

interface PostDataSource {

    suspend fun fetch(postId: PostId): PostWithUsers?

    suspend fun create(draft: NewPost): PostWithUsers

    suspend fun delete(postId: PostId)

    suspend fun setLike(postId: PostId, liked: Boolean): LikeState

    suspend fun setBookmark(postId: PostId, bookmarked: Boolean): Boolean

    suspend fun report(
        postId: PostId,
        reason: ReportReason,
        details: String,
    )
}

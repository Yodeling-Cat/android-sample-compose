package uno.lux.mosaic.post.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.data.network.SetLikeRequestDto
import uno.lux.mosaic.common.data.network.asPart
import uno.lux.mosaic.common.data.network.asTextPart
import uno.lux.mosaic.common.data.network.notFoundAsNull
import uno.lux.mosaic.post.data.PostDataSource
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.NewPostMedia
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostWithUsers
import uno.lux.mosaic.user.data.network.UserMapper

class NetworkPostDataSource(
    private val api: PostApi,
) : PostDataSource {

    override suspend fun fetch(postId: PostId): PostWithUsers? = withContext(Dispatchers.IO) {
        notFoundAsNull { api.getPost(postId) }?.toPostWithUsers()
    }

    override suspend fun create(draft: NewPost): PostWithUsers = withContext(Dispatchers.IO) {
        val media = draft.media

        api
            .createPost(
                title = draft.title.asTextPart(),
                body = draft.body.asTextPart(),
                images = (media as? NewPostMedia.Images)?.files.orEmpty().map { it.asPart("images[]") },
                video = (media as? NewPostMedia.Video)?.file?.asPart("video"),
            ).toPostWithUsers()
    }

    override suspend fun delete(postId: PostId) = withContext(Dispatchers.IO) {
        api.deletePost(postId)
    }

    override suspend fun setLike(postId: PostId, liked: Boolean): LikeState = withContext(Dispatchers.IO) {
        val result = api.setLike(postId, SetLikeRequestDto(liked)).data

        LikeState(isLiked = result.isLiked, likeCount = result.likeCount)
    }

    override suspend fun setBookmark(postId: PostId, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {
        api.setBookmark(postId, SetBookmarkRequestDto(bookmarked)).data.isBookmarked
    }

    override suspend fun report(
        postId: PostId,
        reason: ReportReason,
        details: String,
    ) = withContext(Dispatchers.IO) {
        api.reportPost(
            postId = postId,
            report = ReportPostRequestDto(
                reason = reason.toDto(),
                details = details.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun PostResponse.toPostWithUsers() = PostWithUsers(
        post = PostMapper.map(data),
        users = included.users.map { UserMapper.map(it) },
    )
}

package uno.lux.mosaic.comment.data.network

import uno.lux.mosaic.common.data.network.CursorPageDto
import uno.lux.mosaic.common.data.network.LikeStateDto
import uno.lux.mosaic.common.data.network.LikeStateResponse
import uno.lux.mosaic.common.data.network.SetLikeRequestDto
import uno.lux.mosaic.common.data.network.emptyPage
import uno.lux.mosaic.user.data.network.stubAuthor
import java.time.Instant

class FakeCommentApi(
    private val comments: List<CommentDto> = emptyList(),
    private val page: CursorPageDto = emptyPage,
    val likeResult: LikeStateDto = LikeStateDto(isLiked = true, likeCount = 1),
) : CommentApi {

    val likeRequests = mutableListOf<Pair<String, SetLikeRequestDto>>()

    val commentCursors = mutableListOf<String?>()

    override suspend fun getComments(postId: String, cursor: String?, limit: Int): CommentListResponse {
        commentCursors += cursor

        return CommentListResponse(data = comments, page = page)
    }

    override suspend fun addComment(postId: String, body: AddCommentRequestDto): CommentResponse =
        CommentResponse(
            CommentDto(
                id = "c-new",
                text = body.text,
                createdAt = Instant.parse("2025-01-01T00:00:00.000Z"),
                likeCount = 0,
                isLiked = false,
                author = stubAuthor,
            ),
        )

    override suspend fun setCommentLike(
        postId: String,
        commentId: String,
        body: SetLikeRequestDto,
    ): LikeStateResponse {
        likeRequests += commentId to body

        return LikeStateResponse(likeResult)
    }
}

fun commentDto(
    id: String,
    text: String,
    isLiked: Boolean = false,
    likeCount: Int = 0,
) = CommentDto(
    id = id,
    text = text,
    createdAt = Instant.parse("2025-01-01T00:00:00.000Z"),
    likeCount = likeCount,
    isLiked = isLiked,
    author = stubAuthor,
)

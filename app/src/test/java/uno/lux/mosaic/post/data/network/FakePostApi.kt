package uno.lux.mosaic.post.data.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import uno.lux.mosaic.common.data.network.LikeStateDto
import uno.lux.mosaic.common.data.network.LikeStateResponse
import uno.lux.mosaic.common.data.network.SetLikeRequestDto
import uno.lux.mosaic.common.data.network.notFoundException
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.user.data.network.SideloadedUsers
import uno.lux.mosaic.user.data.network.UserDto
import uno.lux.mosaic.user.data.network.stubAuthor
import java.time.Instant

class FakePostApi(
    /** What [getPost] serves; an id that isn't here 404s, the way the real API does. */
    private val postById: Map<String, PostDto> = emptyMap(),
    /**
     * The users [getPost] and [createPost] sideload, keyed by ID. A post whose author isn't here
     * is served with an empty sideload — the payload a server that forgot to send one would give.
     */
    private val usersById: Map<String, UserDto> = mapOf(stubAuthor.id to stubAuthor),
    val likeResult: LikeStateDto = LikeStateDto(isLiked = true, likeCount = 1),
    val bookmarkResult: BookmarkStateDto = BookmarkStateDto(isBookmarked = true),
) : PostApi {

    /** IDs passed to [getPost], in call order, for test assertions. */
    val fetchedPostIds = mutableListOf<String>()

    /** Thrown by [getPost] instead of answering, so tests can drive the failure path. */
    var getPostError: Exception? = null

    override suspend fun getPost(postId: String): PostResponse {
        fetchedPostIds += postId
        getPostError?.let { throw it }

        val dto = postById[postId] ?: throw notFoundException("Post '$postId' was not found")
        return dto.asResponse()
    }

    /** Wraps a post the way the server does: the post, plus the one user it names as its author. */
    private fun PostDto.asResponse() = PostResponse(
        data = this,
        included = SideloadedUsers(users = listOfNotNull(usersById[authorId])),
    )

    /** The multipart parts of the most recent [createPost] call, for test assertions. */
    var lastCreatePostParts: CreatePostParts? = null
        private set

    /** The parts [createPost] received, decoded back to the values a test can compare against. */
    data class CreatePostParts(
        val title: String,
        val body: String,
        val images: List<UploadedPart>,
        val video: UploadedPart? = null,
    )

    /** One `images[]` part: the filename it declared and the bytes it carried. */
    data class UploadedPart(
        val filename: String?,
        val bytes: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UploadedPart) return false

            return filename == other.filename && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * (filename?.hashCode() ?: 0) + bytes.contentHashCode()
    }

    override suspend fun createPost(
        title: RequestBody,
        body: RequestBody,
        images: List<MultipartBody.Part>,
        video: MultipartBody.Part?,
    ): PostResponse {
        val titleText = title.readText()
        val bodyText = body.readText()
        lastCreatePostParts = CreatePostParts(
            title = titleText,
            body = bodyText,
            images = images.map { part ->
                UploadedPart(filename = part.filename(), bytes = part.body.readBytes())
            },
            video = video?.let {
                UploadedPart(filename = it.filename(), bytes = it.body.readBytes())
            },
        )

        return postDto("p-new", authorId = stubAuthor.id)
            .copy(title = titleText, body = bodyText)
            .asResponse()
    }

    // Retrofit hands the fake real RequestBody parts, so the assertions read them back out.
    private fun RequestBody.readText() = Buffer().also { writeTo(it) }.readUtf8()

    private fun RequestBody.readBytes() = Buffer().also { writeTo(it) }.readByteArray()

    /** The `filename="…"` of a part's Content-Disposition header, which is how the server names it. */
    private fun MultipartBody.Part.filename(): String? =
        headers
            ?.get("Content-Disposition")
            ?.substringAfter("filename=\"", missingDelimiterValue = "")
            ?.substringBefore('"')
            ?.takeIf { it.isNotEmpty() }

    /** IDs passed to [deletePost], in call order, for test assertions. */
    val deletedPostIds = mutableListOf<String>()

    override suspend fun deletePost(postId: String) {
        deletedPostIds += postId
    }

    /** The `(postId, body)` pairs passed to [setLike], in call order, for test assertions. */
    val likeRequests = mutableListOf<Pair<String, SetLikeRequestDto>>()

    override suspend fun setLike(postId: String, body: SetLikeRequestDto): LikeStateResponse {
        likeRequests += postId to body

        return LikeStateResponse(likeResult)
    }

    /** The `(postId, body)` pairs passed to [setBookmark], in call order, for test assertions. */
    val bookmarkRequests = mutableListOf<Pair<String, SetBookmarkRequestDto>>()

    override suspend fun setBookmark(postId: String, body: SetBookmarkRequestDto): BookmarkStateResponse {
        bookmarkRequests += postId to body

        return BookmarkStateResponse(bookmarkResult)
    }

    /** The reports passed to [reportPost], in call order, for test assertions. */
    val reports = mutableListOf<Pair<String, ReportPostRequestDto>>()

    override suspend fun reportPost(postId: String, report: ReportPostRequestDto) {
        reports += postId to report
    }
}

/** The one post projection every endpoint serves — feed page, profile tab and single post alike. */
fun postDto(
    id: String,
    authorId: String,
    createdAt: Instant = Instant.parse("2025-01-01T00:00:00.000Z"),
    isLiked: Boolean = false,
    isBookmarked: Boolean = false,
    likeCount: Int = 0,
    commentCount: Int = 0,
    album: AlbumDto? = null,
    video: VideoDto? = null,
) = PostDto(
    id = id,
    url = testPostUrl(id),
    title = "Title $id",
    body = "Body $id",
    createdAt = createdAt,
    authorId = authorId,
    likeCount = likeCount,
    commentCount = commentCount,
    isLiked = isLiked,
    isBookmarked = isBookmarked,
    album = album,
    video = video,
)

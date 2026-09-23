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
    private val postById: Map<String, PostDto> = emptyMap(),
    private val usersById: Map<String, UserDto> = mapOf(stubAuthor.id to stubAuthor),
    val likeResult: LikeStateDto = LikeStateDto(isLiked = true, likeCount = 1),
    val bookmarkResult: BookmarkStateDto = BookmarkStateDto(isBookmarked = true),
) : PostApi {

    val fetchedPostIds = mutableListOf<String>()

    var getPostError: Exception? = null

    override suspend fun getPost(postId: String): PostResponse {
        fetchedPostIds += postId
        getPostError?.let { throw it }

        val dto = postById[postId] ?: throw notFoundException("Post '$postId' was not found")
        return dto.asResponse()
    }

    private fun PostDto.asResponse() = PostResponse(
        data = this,
        included = SideloadedUsers(users = listOfNotNull(usersById[authorId])),
    )

    var lastCreatePostParts: CreatePostParts? = null
        private set

    data class CreatePostParts(
        val title: String,
        val body: String,
        val images: List<UploadedPart>,
        val video: UploadedPart? = null,
    )

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

    private fun MultipartBody.Part.filename(): String? =
        headers
            ?.get("Content-Disposition")
            ?.substringAfter("filename=\"", missingDelimiterValue = "")
            ?.substringBefore('"')
            ?.takeIf { it.isNotEmpty() }

    val deletedPostIds = mutableListOf<String>()

    override suspend fun deletePost(postId: String) {
        deletedPostIds += postId
    }

    val likeRequests = mutableListOf<Pair<String, SetLikeRequestDto>>()

    override suspend fun setLike(postId: String, body: SetLikeRequestDto): LikeStateResponse {
        likeRequests += postId to body

        return LikeStateResponse(likeResult)
    }

    val bookmarkRequests = mutableListOf<Pair<String, SetBookmarkRequestDto>>()

    override suspend fun setBookmark(postId: String, body: SetBookmarkRequestDto): BookmarkStateResponse {
        bookmarkRequests += postId to body

        return BookmarkStateResponse(bookmarkResult)
    }

    val reports = mutableListOf<Pair<String, ReportPostRequestDto>>()

    override suspend fun reportPost(postId: String, report: ReportPostRequestDto) {
        reports += postId to report
    }
}

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

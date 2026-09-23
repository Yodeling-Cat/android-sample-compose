package uno.lux.mosaic.profile.data.network

import uno.lux.mosaic.common.data.network.emptyPage
import uno.lux.mosaic.user.data.network.SideloadedUsers

internal val emptyPostsWithAuthors = PostsWithAuthorsResponse(
    data = emptyList(),
    included = SideloadedUsers(users = emptyList()),
    page = emptyPage,
)

class FakeProfileApi(
    private val profileStats: Map<String, ProfileStatsDto> = emptyMap(),
    private val userPostsResponse: PostsWithAuthorsResponse = emptyPostsWithAuthors,
    private val bookmarksResponse: PostsWithAuthorsResponse = emptyPostsWithAuthors,
    private val likesResponse: PostsWithAuthorsResponse = emptyPostsWithAuthors,
) : ProfileApi {

    override suspend fun getProfileStats(id: String): ProfileStatsResponse =
        ProfileStatsResponse(profileStats[id] ?: ProfileStatsDto(postsCount = 0))

    val userPostCalls = mutableListOf<Pair<String, String?>>()

    override suspend fun getUserPosts(
        id: String,
        cursor: String?,
        limit: Int,
    ): PostsWithAuthorsResponse {
        userPostCalls += id to cursor
        return userPostsResponse
    }

    val bookmarkCalls = mutableListOf<Pair<String, String?>>()

    override suspend fun getBookmarks(
        id: String,
        cursor: String?,
        limit: Int,
    ): PostsWithAuthorsResponse {
        bookmarkCalls += id to cursor
        return bookmarksResponse
    }

    val likeCalls = mutableListOf<Pair<String, String?>>()

    override suspend fun getLikes(
        id: String,
        cursor: String?,
        limit: Int,
    ): PostsWithAuthorsResponse {
        likeCalls += id to cursor
        return likesResponse
    }
}

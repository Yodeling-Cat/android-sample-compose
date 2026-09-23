package uno.lux.mosaic.profile.ui

import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.ui.PostCardData
import uno.lux.mosaic.profile.data.domain.Profile
import uno.lux.mosaic.user.data.domain.User

data class ProfileScreenData(
    val user: User,
    val profile: Profile,
    val posts: List<Post>,
    val postsEndReached: Boolean = true,
    val bookmarks: ProfilePostList? = null,
    val likes: ProfilePostList? = null,
    val postsLoadMoreFailed: Boolean = false,
    val bookmarksLoadFailed: Boolean = false,
    val likesLoadFailed: Boolean = false,
)

data class ProfilePostList(
    val posts: List<PostCardData>,
    val endReached: Boolean = true,
)

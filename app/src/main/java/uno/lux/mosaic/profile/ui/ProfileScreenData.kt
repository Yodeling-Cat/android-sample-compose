package uno.lux.mosaic.profile.ui

import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.ui.PostCardData
import uno.lux.mosaic.profile.data.domain.Profile
import uno.lux.mosaic.user.data.domain.User

/**
 * ViewModel-level aggregate for the profile screen: the resolved user, their profile metadata,
 * their posts, the posts they liked, and — on their own profile only — the posts they saved.
 */
data class ProfileScreenData(
    val user: User,
    val profile: Profile,
    val posts: List<Post>,
    val postsEndReached: Boolean = true,
    val bookmarks: ProfilePostList? = null,
    val likes: ProfilePostList? = null,
)

/**
 * One on-demand tab's content — Saved or Likes — absent until that tab is first opened. Its posts
 * carry their authors, unlike [ProfileScreenData.posts], which are all by the profile's own user:
 * a saved or liked post can be by anyone.
 */
data class ProfilePostList(
    val posts: List<PostCardData>,
    val endReached: Boolean = true,
)

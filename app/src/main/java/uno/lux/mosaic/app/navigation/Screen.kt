package uno.lux.mosaic.app.navigation

import kotlinx.serialization.Serializable
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video

/**
 * The full-screen pages of the app. [Shell] is the permanent root carrying the navigation-suite
 * tabs; [Profile], [Settings] and [FullscreenVideo] are pushed over it (settings can stack on top
 * of a profile, too). Screens are [Serializable] so the back stack survives configuration changes
 * and process death.
 *
 * A `Screen` describes *what to show*, not *which open copy of it* — the back stack is made of
 * [BackStackEntry], which pairs a screen with the identity its state is scoped to. So two equal
 * screens are two pushes of the same page, and equality is safe to ask about: it is what
 * [Navigator.goToSingleTop] compares.
 */
@Serializable
sealed interface Screen {

    /**
     * Pins every push of this screen to one back-stack identity, so all of them share a single
     * ViewModel and one set of `rememberSaveable` state; `null` — the default — gives each push
     * its own, which is what nearly every page wants.
     *
     * Override it with a constant for a page that is genuinely one thing wherever it is opened,
     * or with a value derived from the screen's own arguments (`"post-$postId"`) to share per
     * argument. Always declare it as `get() = …` and never as an initialized property: a property
     * with a backing field would be pulled into the serialized form of the key.
     */
    val sharedId: String? get() = null

    /**
     * The adaptive navigation shell hosting the top-level tabs; always the bottom entry, and the
     * only screen there is ever exactly one of — which is why it names its identity outright.
     */
    @Serializable
    data object Shell : Screen {
        override val sharedId: String get() = "shell"
    }

    /** A user's profile page, opened from a post's author header. */
    @Serializable
    data class Profile(
        val userId: UserId,
    ) : Screen

    /** The settings page, opened from the gear action any screen's top bar carries. */
    @Serializable
    data object Settings : Screen

    /**
     * The signed-in user's profile editor, opened from their own profile's Edit button.
     * Always edits the current user, so it carries no arguments.
     */
    @Serializable
    data object EditProfile : Screen

    /**
     * The post composer. Reached from the navigation suite's Create item, which pushes it over
     * the whole shell rather than swapping the shell's content — so it covers the tab bar and
     * the tab the user was on stays selected underneath.
     */
    @Serializable
    data object CreatePost : Screen

    /**
     * The full-screen video page. Opened by a profile-grid video (which it loads itself), by an
     * inline post player's fullscreen control (whose running player it reuses), or by the
     * composer previewing a picked clip. [url] — a post video's stream URL or a picked clip's
     * content URI — is the player's identity, keying the shared player so the inline →
     * full-screen hand-off keeps the same instance. [title] describes the video for
     * accessibility; a clip picked from disk has none.
     */
    @Serializable
    data class FullscreenVideo(
        val url: String,
        val title: String? = null,
    ) : Screen {
        constructor(video: Video) : this(video.videoUrl, video.title)
    }

    /** A post's detail page: full content, media, and the comment thread. */
    @Serializable
    data class PostDetail(
        val postId: PostId,
    ) : Screen

    /**
     * Full-screen album image viewer with a horizontal pager. [images] holds what to show — a
     * post's album image URLs or the composer's picked photo URIs; [initialIndex] opens the
     * pager at the image the user tapped.
     */
    @Serializable
    data class AlbumViewer(
        val images: List<String>,
        val initialIndex: Int,
    ) : Screen
}

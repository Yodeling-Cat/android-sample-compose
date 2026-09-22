package uno.lux.mosaic.app.fixtures

import uno.lux.mosaic.album.data.domain.Album
import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.video.data.domain.Video
import java.time.Duration
import java.time.Instant

/**
 * Stand-in content for the in-memory repository and Compose previews. Timestamps are
 * anchored to "now" at first access so the relative labels ("4m", "2h", "3d") stay
 * believable whenever the app is launched.
 */
internal val SampleUsers = listOf(
    User(
        id = "u1",
        nickname = "Ada Lovelace",
        handle = "@countess",
        age = 36,
        gender = "Woman",
        location = "London, England",
        bio = "Mathematician & writer. The Analytical Engine weaves algebra the way the loom " +
            "weaves flowers. Poetical science, mostly.",
        followerCount = 128_400,
        followingCount = 212,
    ),
    User(
        id = "u2",
        nickname = "Grace Hopper",
        handle = "@amazinggrace",
        age = 85,
        gender = "Woman",
        location = "Arlington, Virginia",
        bio = "Rear Admiral. Compiler pioneer. It's easier to ask forgiveness than permission.",
        followerCount = 342_000,
        followingCount = 180,
    ),
    User(
        id = "u3",
        nickname = "Alan Turing",
        handle = "@enigma",
        age = 41,
        gender = "Man",
        location = "Manchester, England",
        bio = "Asking the only question that matters: can a machine play the imitation game?",
        followerCount = 891_000,
        followingCount = 73,
    ),
    User(
        id = "u4",
        nickname = "Margaret Hamilton",
        handle = "@mhamilton",
        age = 88,
        gender = "Woman",
        location = "Cambridge, Massachusetts",
        bio = "I coined \"software engineering\" so they'd take the code as seriously as the " +
            "hardware. Apollo guidance, priority scheduling.",
        followerCount = 154_300,
        followingCount = 96,
    ),
    User(
        id = "u5",
        nickname = "Linus",
        handle = "@torvalds",
        age = 54,
        gender = "Man",
        location = "Portland, Oregon",
        bio = "Just a hobby, won't be big. Talk is cheap — show me the code.",
        followerCount = 5_200_000,
        followingCount = 12,
    ),
)

/** The sample user signed in as "me" — their own profile shows the Edit-profile action. */
const val LOGGED_IN_USER_ID = "u1"

/**
 * The stand-in stream each sample [Video] points at — a short, freely hosted MP4, one per video.
 * Sharing a stream between two videos is what to avoid here: the shared player keys playback on
 * the URL, so two videos pointing at the same file are one playback and cannot play independently.
 *
 * They have to *look* different too. The host's `sample-3.mp4` and `sample-5.mp4` are the same
 * clip under two names, which leaves a playback bug indistinguishable from correct behaviour.
 */
private const val IMITATION_GAME_VIDEO_URL = "https://getsamplefiles.com/download/mp4/sample-2.mp4"
private const val KERNEL_BOOT_VIDEO_URL = "https://getsamplefiles.com/download/mp4/sample-5.mp4"

/**
 * Poster frames for the sample clips. An uploaded video's is extracted server-side, but the sample
 * clips are external files the server never read, so — exactly as its own seed data does — they
 * point at a hosted JPEG and carry fixed dimensions to match.
 */
private const val IMITATION_GAME_THUMBNAIL_URL = "https://getsamplefiles.com/download/jpg/sample-1.jpg"
private const val KERNEL_BOOT_THUMBNAIL_URL = "https://getsamplefiles.com/download/jpg/sample-4.jpg"

/** The stand-in photos the "Engine sketches" album shows, as freely hosted JPEGs. */
private val EngineSketchImages = listOf(
    "https://getsamplefiles.com/download/jpg/sample-1.jpg",
    "https://getsamplefiles.com/download/jpg/sample-2.jpg",
    "https://getsamplefiles.com/download/jpg/sample-3.jpg",
)

/**
 * The "Launch room" album's photos. Deliberately its own set, and a different count from
 * [EngineSketchImages], so the two albums are told apart at a glance and the pager and item-count
 * badge are exercised at more than one length.
 */
private val LaunchRoomImages = listOf(
    "https://getsamplefiles.com/download/jpg/sample-4.jpg",
    "https://getsamplefiles.com/download/jpg/sample-5.jpg",
)

/**
 * Sample comments seeded per post id; timestamps are anchored to first access.
 *
 * Declared *above* [SamplePosts] on purpose: a post's `commentCount` is read from here through
 * [sampleCommentCount], and top-level properties in a file initialize in declaration order, so
 * moving this back below the posts would have every one of them counting an empty map.
 */
internal val SampleComments: Map<PostId, List<Comment>> =
    buildSampleComments(Instant.now())

/**
 * How many comments the fixtures give [postId] — what its `commentCount` is, rather than a number
 * written beside one. The server derives the count from its comment rows the same way, and these
 * fixtures used to claim 17, 51 and 612 against threads holding two or three, which made every
 * preview of a post card disagree with the detail page it opened.
 */
private fun sampleCommentCount(postId: PostId) =
    SampleComments.getValue(postId).size

private fun buildSampleComments(now: Instant): Map<PostId, List<Comment>> = mapOf(
    // p1 "The engine weaves algebraic patterns" — post is 4m old
    "p1" to listOf(
        Comment(
            "c1p1", SampleUsers[1], now.minus(Duration.ofMinutes(3)),
            "The loom analogy is poetic — your best one yet.", 24,
        ),
        Comment(
            "c2p1", SampleUsers[2], now.minus(Duration.ofMinutes(1)),
            "Did Ada ever see a Jacquard loom? I believe she did.", 9,
        ),
    ),
    // p2 "Found the bug" — post is 38m old
    "p2" to listOf(
        Comment(
            "c1p2", SampleUsers[2], now.minus(Duration.ofMinutes(35)),
            "First recorded debugging session. The logbook is incredible.", 61,
        ),
        Comment(
            "c2p2", SampleUsers[3], now.minus(Duration.ofMinutes(20)),
            "It's always the actual bugs, isn't it.", 18,
        ),
    ),
    // p3 "Can machines think?" — post is 2h old
    "p3" to listOf(
        Comment(
            "c1p3", SampleUsers[0], now.minus(Duration.ofMinutes(115)),
            "The imitation game is really a test of our assumptions, not the machine.", 44,
        ),
        Comment(
            "c2p3", SampleUsers[3], now.minus(Duration.ofMinutes(90)),
            "The question itself is the insight. Brilliant framing.", 12,
        ),
        Comment(
            "c3p3", SampleUsers[4], now.minus(Duration.ofMinutes(38)),
            "Philosophy embedded in a practical test.", 3,
        ),
    ),
    // p4 "Priority scheduling saved the landing" — post is 6h old
    "p4" to listOf(
        Comment(
            "c1p4", SampleUsers[0], now.minus(Duration.ofMinutes(350)),
            "The 1202 alarm story is one of the best in engineering. They kept going.", 31,
        ),
        Comment(
            "c2p4", SampleUsers[2], now.minus(Duration.ofHours(3)),
            "Priority scheduling is underappreciated in computing history.", 7,
        ),
    ),
    // p5 "Just a hobby, won't be big" — post is 1d old
    "p5" to listOf(
        Comment(
            "c1p5", SampleUsers[1], now.minus(Duration.ofHours(20)),
            "Famous last words. The kernel is still running.", 22,
        ),
        Comment(
            "c2p5", SampleUsers[0], now.minus(Duration.ofHours(16)),
            "I love how this turned out to be just a hobby.", 15,
        ),
    ),
    // p6 "On numbers and music" — post is 3d old
    "p6" to listOf(
        Comment(
            "c1p6", SampleUsers[1], now.minus(Duration.ofDays(2)),
            "The Analytical Engine composing music — a beautiful idea.", 11,
        ),
    ),
)

/**
 * Sample posts stand in for server payloads, so they carry the shareable link the way the server
 * would send it rather than leaving the app to assemble one.
 */
private fun samplePostUrl(id: PostId) =
    "https://mosaic.tree-among-shrubs.com/p/$id"

internal val SamplePosts: List<Post> =
    buildSamplePosts(Instant.now())

private fun buildSamplePosts(now: Instant): List<Post> = listOf(
    Post(
        id = "p1",
        url = samplePostUrl("p1"),
        authorId = SampleUsers[0].id,
        title = "The engine weaves algebraic patterns",
        body = "Just like the Jacquard loom weaves flowers and leaves. A machine need not " +
            "be limited to numbers — give it the right notation and it can compose.",
        createdAt = now.minus(Duration.ofMinutes(4)),
        likeCount = 128,
        commentCount = sampleCommentCount("p1"),
        isLiked = false,
        album = Album(
            id = "pa1",
            title = "Engine sketches",
            itemCount = EngineSketchImages.size,
            images = EngineSketchImages,
        ),
    ),
    Post(
        id = "p2",
        url = samplePostUrl("p2"),
        authorId = SampleUsers[1].id,
        title = "Found the bug",
        body = "It was an actual moth, taped into the logbook at 15:45. First recorded " +
            "case of debugging being literal. Onward to the next nanosecond.",
        createdAt = now.minus(Duration.ofMinutes(38)),
        likeCount = 342,
        commentCount = sampleCommentCount("p2"),
        isLiked = true,
        isBookmarked = true,
    ),
    Post(
        id = "p3",
        url = samplePostUrl("p3"),
        authorId = SampleUsers[2].id,
        title = "Can machines think?",
        body = "The question is too meaningless to deserve discussion. So replace it: can a " +
            "machine play the imitation game well enough that you can't tell?",
        createdAt = now.minus(Duration.ofHours(2)),
        likeCount = 891,
        commentCount = sampleCommentCount("p3"),
        video = Video(
            id = "pv3",
            title = "The imitation game, in five seconds",
            durationSeconds = 5,
            videoUrl = IMITATION_GAME_VIDEO_URL,
            width = 1280,
            height = 720,
            thumbnailUrl = IMITATION_GAME_THUMBNAIL_URL,
            thumbnailWidth = 640,
            thumbnailHeight = 360,
        ),
    ),
    Post(
        id = "p4",
        url = samplePostUrl("p4"),
        authorId = SampleUsers[3].id,
        title = "Priority scheduling saved the landing",
        body = "Three minutes before touchdown the computer flashed a 1202 alarm. Because we " +
            "designed it to shed low-priority work under overload, it kept the essentials " +
            "running. Apollo 11 landed anyway.",
        createdAt = now.minus(Duration.ofHours(6)),
        likeCount = 1543,
        commentCount = sampleCommentCount("p4"),
        isLiked = true,
        isBookmarked = true,
        video = null,
        album = Album(
            id = "pa4",
            title = "Launch room",
            itemCount = LaunchRoomImages.size,
            images = LaunchRoomImages,
        ),
    ),
    Post(
        id = "p5",
        url = samplePostUrl("p5"),
        authorId = SampleUsers[4].id,
        title = "Just a hobby, won't be big",
        body = "I'm doing a (free) operating system — nothing professional like GNU — for " +
            "386(486) AT clones. It probably never will support anything other than AT " +
            "hard disks, as that's all I have. :)",
        createdAt = now.minus(Duration.ofDays(1)),
        likeCount = 5200,
        commentCount = sampleCommentCount("p5"),
        video = Video(
            id = "pv5",
            title = "Booting the kernel",
            durationSeconds = 5,
            videoUrl = KERNEL_BOOT_VIDEO_URL,
            width = 1920,
            height = 1080,
            thumbnailUrl = KERNEL_BOOT_THUMBNAIL_URL,
            thumbnailWidth = 640,
            thumbnailHeight = 360,
        ),
    ),
    Post(
        id = "p6",
        url = samplePostUrl("p6"),
        authorId = SampleUsers[0].id,
        title = "On numbers and music",
        body = "Supposing the relations of pitched sounds could be expressed by the engine, it " +
            "might compose elaborate pieces of music of any degree of complexity.",
        createdAt = now.minus(Duration.ofDays(3)),
        likeCount = 64,
        commentCount = sampleCommentCount("p6"),
    ),
)

package uno.lux.mosaic.post.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import uno.lux.mosaic.R
import uno.lux.mosaic.app.util.compactCount
import uno.lux.mosaic.app.util.relativeTime
import uno.lux.mosaic.common.asText
import uno.lux.mosaic.designsystem.components.debouncedClickable
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.ui.Avatar
import uno.lux.mosaic.video.data.domain.Video
import uno.lux.mosaic.video.ui.VideoPostPlayer

/*
 * The shared anatomy of a post — header, body, media, actions — composed both by [PostCard]
 * (the feed and profile rows) and by the post detail screen. The feed card adds an overflow menu
 * and a divider and opens the post on tap; the detail screen draws the post as its subject.
 */

/**
 * Avatar + identity (nickname, `handle · time`) as one tappable target that opens the author's
 * profile, with an optional [trailing] affordance kept as its own target beside it.
 */
@Composable
internal fun PostAuthorHeader(
    post: Post,
    author: User,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    // A trailing IconButton carries the inset its 48dp touch target needs, so the row's own end
    // padding tightens to match when one is present.
    val endPadding = if (trailing == null) 14.dp else 6.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 14.dp, end = endPadding, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .debouncedClickable(onClick = onOpenProfile)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user = author, size = 42.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = author.nickname,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${author.handle} · ${relativeTime(post.createdAt).asText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMosaicColors.current.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
internal fun PostBody(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    maxBodyLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick ==
        null
    ) {
        Modifier
    } else {
        Modifier.debouncedClickable(onClick = onClick)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        if (maxBodyLines == Int.MAX_VALUE) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PostBodyText(body = body, maxLines = maxBodyLines)
        }
    }
}

/**
 * The body copy as a perex, clipped to [maxLines]. When it overflows that cap, the last line is
 * trimmed to leave exactly enough room for an inline "… Show more" suffix.
 */
@Composable
private fun PostBodyText(body: String, maxLines: Int) {
    val showMore = stringResource(R.string.post_show_more)
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val moreStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    val ellipsis = "… "
    val measurer = rememberTextMeasurer()

    // The suffix appended when the body overflows, and the width it must be given on the last line.
    val suffix = remember(showMore, moreStyle) {
        buildAnnotatedString {
            append(ellipsis)
            withStyle(moreStyle) { append(showMore) }
        }
    }
    val suffixWidth = remember(suffix, bodyStyle) {
        measurer.measure(text = suffix, style = bodyStyle).size.width
    }

    var display by remember(body, maxLines) { mutableStateOf(AnnotatedString(body)) }

    Text(
        text = display,
        style = bodyStyle,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            // Only the untrimmed body can overflow; once trimmed it fits, so this runs at most once.
            if (result.hasVisualOverflow && display.text == body) {
                val lastLine = result.lineCount - 1
                val lineMid = (result.getLineTop(lastLine) + result.getLineBottom(lastLine)) / 2f
                val cutX = (result.size.width - suffixWidth).toFloat().coerceAtLeast(0f)
                val cut = result
                    .getOffsetForPosition(Offset(x = cutX, y = lineMid))
                    .coerceIn(0, body.length)

                display = buildAnnotatedString {
                    append(body.substring(0, cut).trimEnd())
                    append(suffix)
                }
            }
        },
    )
}

/** A post's attached media, if any: an album's photo strip and/or its video player. */
@Composable
internal fun PostMedia(
    post: Post,
    onOpenVideo: (Video) -> Unit,
    onOpenAlbum: (List<String>, initialIndex: Int) -> Unit,
) {
    if (post.album != null) {
        Spacer(Modifier.height(12.dp))
        AlbumPostGallery(
            album = post.album,
            onOpenImage = { index -> onOpenAlbum(post.album.images, index) },
        )
    }

    if (post.video != null) {
        Spacer(Modifier.height(12.dp))
        VideoPostPlayer(
            video = post.video,
            onOpenFullscreen = onOpenVideo,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
internal fun PostActions(
    post: Post,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCommentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Crossfade the heart between muted and coral so the color tracks the pop rather than snapping.
    val likeTint by animateColorAsState(
        targetValue = if (post.isLiked) LocalMosaicColors.current.like else muted,
        label = "likeTint",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            iconRes = if (post.isLiked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border,
            contentDescriptionRes = if (post.isLiked) R.string.post_action_unlike else R.string.post_action_like,
            label = compactCount(post.likeCount).asText(),
            tint = likeTint,
            onClick = onToggleLike,
            iconModifier = Modifier.pop(post.isLiked),
        )
        Spacer(Modifier.width(2.dp))
        ActionButton(
            iconRes = R.drawable.ic_comment,
            contentDescriptionRes = R.string.post_action_comment,
            label = compactCount(post.commentCount).asText(),
            tint = muted,
            onClick = onCommentClick,
        )
        Spacer(Modifier.weight(1f))
        ActionButton(
            iconRes = if (post.isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border,
            contentDescriptionRes = if (post.isBookmarked) R.string.post_action_unbookmark else R.string.post_action_bookmark,
            label = null,
            tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else muted,
            onClick = onToggleBookmark,
            iconModifier = Modifier.pop(post.isBookmarked),
        )
    }
}

/** Pill-shaped action: icon + optional count, both tinted by [tint] (accented when active). */
@Composable
private fun ActionButton(
    iconRes: Int,
    @StringRes contentDescriptionRes: Int,
    label: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .debouncedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(contentDescriptionRes),
            tint = tint,
            modifier = Modifier
                .size(23.dp)
                .then(iconModifier),
        )
        if (label != null) {
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
        }
    }
}

/** The Mosaic "pop": overshoot to 1.35×, dip to 0.9×, settle — the design's like/save curve. */
private val PopEasing = CubicBezierEasing(0.2f, 1.3f, 0.5f, 1f)

/** Plays the Mosaic "pop" whenever [active] *changes* — the feedback for like and save. */
@Composable
private fun Modifier.pop(active: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    var wasActive by remember { mutableStateOf(active) }

    LaunchedEffect(active) {
        if (active != wasActive) {
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 400
                    1f at 0 using PopEasing
                    1.35f at 140 using PopEasing // 35%
                    0.9f at 240 using PopEasing // 60%
                    1f at 400
                },
            )
        }
        wasActive = active
    }

    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

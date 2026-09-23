package uno.lux.mosaic.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.common.initials
import uno.lux.mosaic.designsystem.theme.Manrope
import uno.lux.mosaic.designsystem.theme.MosaicGradients
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

/**
 * A circular avatar for a [user].
 */
@Composable
fun Avatar(
    user: User,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    Avatar(
        userId = user.id,
        name = user.nickname,
        modifier = modifier,
        size = size,
        imageUrl = user.avatarUrl,
    )
}

/**
 * A circular avatar. With no [imageUrl] it renders the user's initials on a gradient. A non-null
 * [imageUrl] loads the profile photo over that layer, so the initials double as the loading /
 * error fallback.
 */
@Composable
fun Avatar(
    userId: UserId,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    imageUrl: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MosaicGradients.avatarBrush(userId)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            color = Color.White,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
        )

        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Initials only, at a spread of sizes: the layer every avatar starts as, and falls back to. */
@Preview(showBackground = true)
@Composable
private fun AvatarPreview() {
    MosaicTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            SampleUsers.forEachIndexed { index, user ->
                Avatar(
                    userId = user.id,
                    name = user.nickname,
                    size = (24 + index * 8).dp,
                )
            }
        }
    }
}

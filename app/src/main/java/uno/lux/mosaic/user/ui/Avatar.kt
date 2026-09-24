package uno.lux.mosaic.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.common.initials
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.Manrope
import uno.lux.mosaic.designsystem.theme.MosaicGradients
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

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
        isOnline = user.isOnline,
    )
}

@Composable
fun Avatar(
    userId: UserId,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    imageUrl: String? = null,
    isOnline: Boolean = false,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
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

        if (isOnline) {
            OnlineDot(
                avatarSize = size,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun OnlineDot(avatarSize: Dp, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.avatar_online)
    val ringWidth = max(avatarSize * 0.05f, 1.5.dp)

    Box(
        modifier = modifier
            .size(avatarSize * 0.28f)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(ringWidth)
            .background(LocalMosaicColors.current.online, CircleShape)
            .semantics { contentDescription = description },
    )
}

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
                    isOnline = index % 2 == 0,
                )
            }
        }
    }
}

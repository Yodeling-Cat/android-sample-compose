package uno.lux.mosaic.comment.data.network

import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.network.CursorPageDto

@Serializable
data class CommentListResponse(
    val data: List<CommentDto>,
    val page: CursorPageDto,
)

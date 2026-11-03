package uno.lux.mosaic.comment.data.network

import tech.mappie.api.ObjectMappie
import uno.lux.mosaic.comment.domain.Comment

object CommentMapper : ObjectMappie<CommentDto, Comment>() {
    override fun map(from: CommentDto) = mapping()
}

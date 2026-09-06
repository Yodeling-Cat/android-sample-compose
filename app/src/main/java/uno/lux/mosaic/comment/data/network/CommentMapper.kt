package uno.lux.mosaic.comment.data.network

import tech.mappie.api.ObjectMappie
import uno.lux.mosaic.comment.data.domain.Comment

// DTO → domain mapping runs through Mappie (a Kotlin compiler plugin)

object CommentMapper : ObjectMappie<CommentDto, Comment>() {
    override fun map(from: CommentDto) = mapping()
}

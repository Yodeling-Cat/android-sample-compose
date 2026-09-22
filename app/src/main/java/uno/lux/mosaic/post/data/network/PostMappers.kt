package uno.lux.mosaic.post.data.network

import tech.mappie.api.ObjectMappie
import uno.lux.mosaic.album.data.domain.Album
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.video.data.domain.Video

object AlbumMapper : ObjectMappie<AlbumDto, Album>() {
    override fun map(from: AlbumDto) = mapping()
}

object VideoMapper : ObjectMappie<VideoDto, Video>() {
    override fun map(from: VideoDto) = mapping()
}

object PostMapper : ObjectMappie<PostDto, Post>() {
    override fun map(from: PostDto) = mapping()
}

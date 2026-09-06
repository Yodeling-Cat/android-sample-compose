package uno.lux.mosaic.album.data.domain

typealias AlbumId = String

data class Album(
    val id: AlbumId,
    val title: String,
    val itemCount: Int,
    val images: List<String> = emptyList(),
)

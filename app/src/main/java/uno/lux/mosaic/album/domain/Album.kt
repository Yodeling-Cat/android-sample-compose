package uno.lux.mosaic.album.domain

typealias AlbumId = String

data class Album(
    val id: AlbumId,
    val title: String,
    val itemCount: Int,
    val images: List<String> = emptyList(),
)

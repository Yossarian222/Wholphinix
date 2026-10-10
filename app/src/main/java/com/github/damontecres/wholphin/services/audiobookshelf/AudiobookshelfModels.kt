package com.github.damontecres.wholphin.services.audiobookshelf

import kotlinx.serialization.Serializable

/**
 * Saved connection settings for Audiobookshelf. Stored locally on the device only, never in the repository.
 *
 * @param lanUrl primary address, e.g. http://192.168.1.201:13378
 * @param tailscaleUrl optional fallback address used when the LAN address is unreachable
 * @param token Audiobookshelf API token
 */
@Serializable
data class AbsConfig(
    val lanUrl: String = "",
    val tailscaleUrl: String = "",
    val token: String = "",
) {
    val isComplete: Boolean get() = lanUrl.isNotBlank() && token.isNotBlank()
}

/** A resolved connection that was reachable */
data class AbsConnection(
    val baseUrl: String,
    val token: String,
)

@Serializable
data class AbsLibrary(
    val id: String,
    val name: String,
    val mediaType: String? = null,
)

@Serializable
data class AbsLibrariesResponse(
    val libraries: List<AbsLibrary> = emptyList(),
)

/** A named reference, e.g. a book's author */
@Serializable
data class AbsNamed(
    val id: String? = null,
    val name: String = "",
)

/** A book's series with the book's position in it */
@Serializable
data class AbsSeriesRef(
    val id: String? = null,
    val name: String = "",
    val sequence: String? = null,
)

/**
 * Metadata of a podcast or a book. Podcasts use [author] and [releaseDate], books [authorName] (minified)
 * or [authors] (expanded) and [publishedYear].
 */
@Serializable
data class AbsMetadata(
    val title: String? = null,
    val titleIgnorePrefix: String? = null,
    val subtitle: String? = null,
    val author: String? = null,
    val authorName: String? = null,
    val narratorName: String? = null,
    val seriesName: String? = null,
    val authors: List<AbsNamed> = emptyList(),
    val narrators: List<String> = emptyList(),
    val series: List<AbsSeriesRef> = emptyList(),
    val genres: List<String> = emptyList(),
    val description: String? = null,
    val publishedYear: String? = null,
    val releaseDate: String? = null,
    val publisher: String? = null,
)

/** A chapter of a book or an episode, times in seconds */
@Serializable
data class AbsChapter(
    val id: Int? = null,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val title: String? = null,
)

/** The media of a library item: a podcast (with episodes) or a book (with chapters) */
@Serializable
data class AbsPodcastMedia(
    val metadata: AbsMetadata? = null,
    val numEpisodes: Int? = null,
    val episodes: List<AbsEpisode> = emptyList(),
    val duration: Double? = null,
    val numChapters: Int? = null,
    val numTracks: Int? = null,
    val chapters: List<AbsChapter> = emptyList(),
)

@Serializable
data class AbsLibraryItem(
    val id: String,
    val libraryId: String? = null,
    /** "podcast" or "book" */
    val mediaType: String? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val media: AbsPodcastMedia? = null,
    /** Only in the "items in progress" response: the podcast episode being listened to */
    val recentEpisode: AbsEpisode? = null,
    /** Only in the "items in progress" response */
    val progressLastUpdate: Long? = null,
) {
    val title: String get() = media?.metadata?.title ?: id
    val author: String?
        get() =
            media?.metadata?.let { meta ->
                meta.author?.takeIf { it.isNotBlank() }
                    ?: meta.authorName?.takeIf { it.isNotBlank() }
                    ?: meta.authors
                        .map { it.name }
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                        .takeIf { it.isNotBlank() }
            }
    val isPodcast: Boolean get() = mediaType == "podcast" || (mediaType == null && media?.numEpisodes != null)
    val sortTitle: String get() = media?.metadata?.titleIgnorePrefix?.takeIf { it.isNotBlank() } ?: title
}

@Serializable
data class AbsLibraryItemsResponse(
    val results: List<AbsLibraryItem> = emptyList(),
    val total: Int? = null,
)

@Serializable
data class AbsEpisode(
    val id: String,
    val title: String? = null,
    val duration: Double? = null,
    val publishedAt: Long? = null,
    val index: Int? = null,
    val subtitle: String? = null,
    /** HTML from the feed */
    val description: String? = null,
    val season: String? = null,
    val episode: String? = null,
    val chapters: List<AbsChapter> = emptyList(),
)

@Serializable
data class AbsMediaProgress(
    val libraryItemId: String,
    val episodeId: String? = null,
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
    val progress: Double = 0.0,
    val isFinished: Boolean = false,
    val lastUpdate: Long = 0L,
)

@Serializable
data class AbsUser(
    val mediaProgress: List<AbsMediaProgress> = emptyList(),
)

/** Response of `/api/me/items-in-progress` */
@Serializable
data class AbsItemsInProgressResponse(
    val libraryItems: List<AbsLibraryItem> = emptyList(),
)

/** A series of a book library with its books */
@Serializable
data class AbsSeries(
    val id: String,
    val name: String = "",
    val nameIgnorePrefix: String? = null,
    val addedAt: Long? = null,
    val books: List<AbsLibraryItem> = emptyList(),
)

@Serializable
data class AbsSeriesResponse(
    val results: List<AbsSeries> = emptyList(),
)

@Serializable
data class AbsAuthor(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val imagePath: String? = null,
    val numBooks: Int? = null,
    val addedAt: Long? = null,
    /** Only when requested with `include=items` */
    val libraryItems: List<AbsLibraryItem> = emptyList(),
)

@Serializable
data class AbsAuthorsResponse(
    val authors: List<AbsAuthor> = emptyList(),
)

@Serializable
data class AbsDeviceInfo(
    val clientName: String = "Wholphin",
    val deviceId: String = "wholphin-android-tv",
)

@Serializable
data class AbsPlayRequest(
    val deviceInfo: AbsDeviceInfo = AbsDeviceInfo(),
    val forceDirectPlay: Boolean = true,
    val forceTranscode: Boolean = false,
)

@Serializable
data class AbsAudioTrack(
    val index: Int? = null,
    val contentUrl: String,
    val mimeType: String? = null,
    val duration: Double? = null,
    /** Start of this track within the whole book, in seconds */
    val startOffset: Double? = null,
)

@Serializable
data class AbsPlaySession(
    val id: String,
    val libraryItemId: String,
    val episodeId: String? = null,
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
    val audioTracks: List<AbsAudioTrack> = emptyList(),
    val chapters: List<AbsChapter> = emptyList(),
    val displayTitle: String? = null,
    val displayAuthor: String? = null,
)

@Serializable
data class AbsSyncRequest(
    val currentTime: Double,
    val timeListened: Double,
    val duration: Double,
)

/** Thrown for a non-2xx HTTP response. Not retried on the fallback address. */
class AudiobookshelfHttpException(
    val code: Int,
    path: String,
) : java.io.IOException("Audiobookshelf returned HTTP $code for $path")

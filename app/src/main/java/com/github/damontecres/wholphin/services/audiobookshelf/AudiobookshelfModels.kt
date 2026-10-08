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

@Serializable
data class AbsMetadata(
    val title: String? = null,
    val author: String? = null,
)

@Serializable
data class AbsPodcastMedia(
    val metadata: AbsMetadata? = null,
    val numEpisodes: Int? = null,
    val episodes: List<AbsEpisode> = emptyList(),
)

@Serializable
data class AbsLibraryItem(
    val id: String,
    val media: AbsPodcastMedia? = null,
) {
    val title: String get() = media?.metadata?.title ?: id
    val author: String? get() = media?.metadata?.author
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
)

@Serializable
data class AbsPlaySession(
    val id: String,
    val libraryItemId: String,
    val episodeId: String? = null,
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
    val audioTracks: List<AbsAudioTrack> = emptyList(),
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

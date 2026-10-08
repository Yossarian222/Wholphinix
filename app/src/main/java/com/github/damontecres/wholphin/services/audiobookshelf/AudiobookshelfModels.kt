package com.github.damontecres.wholphin.services.audiobookshelf

import kotlinx.serialization.Serializable

/**
 * Connection settings for the user's Audiobookshelf (ABS) server.
 *
 * Stored via [com.github.damontecres.wholphin.services.KeyValueService].
 */
@Serializable
data class AbsConfig(
    val baseUrl: String = "",
    val token: String = "",
    val username: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

// ---- Wire models. Everything is nullable/defaulted: ABS adds fields between versions. ----

@Serializable
data class AbsLoginResponse(
    val user: AbsUser,
)

@Serializable
data class AbsUser(
    val id: String? = null,
    val username: String? = null,
    // Legacy permanent token, still returned by 2.x
    val token: String? = null,
    // Newer short-lived JWT (2.26+)
    val accessToken: String? = null,
)

@Serializable
data class AbsLibrariesResponse(
    val libraries: List<AbsLibrary> = emptyList(),
)

@Serializable
data class AbsLibrary(
    val id: String,
    val name: String = "",
    /** "podcast" or "book" */
    val mediaType: String = "",
) {
    val isPodcast: Boolean get() = mediaType == "podcast"
}

@Serializable
data class AbsLibraryItemsResponse(
    val results: List<AbsLibraryItem> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class AbsItemsInProgressResponse(
    val libraryItems: List<AbsLibraryItem> = emptyList(),
)

@Serializable
data class AbsLibraryItem(
    val id: String,
    val libraryId: String? = null,
    val mediaType: String? = null,
    val media: AbsMedia? = null,
    /** Only present for items returned by items-in-progress (podcasts) */
    val recentEpisode: AbsEpisode? = null,
) {
    val title: String get() = media?.metadata?.title.orEmpty()
    val author: String? get() = media?.metadata?.author ?: media?.metadata?.authorName
}

@Serializable
data class AbsMedia(
    val metadata: AbsMetadata = AbsMetadata(),
    /** Podcasts only, and only when the item was fetched expanded */
    val episodes: List<AbsEpisode> = emptyList(),
    val numEpisodes: Int? = null,
    /** Books only */
    val duration: Double? = null,
)

@Serializable
data class AbsMetadata(
    val title: String? = null,
    /** Podcasts */
    val author: String? = null,
    /** Books */
    val authorName: String? = null,
    val description: String? = null,
)

@Serializable
data class AbsEpisode(
    val id: String,
    val libraryItemId: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val season: String? = null,
    val episode: String? = null,
    /** Unix millis */
    val publishedAt: Long? = null,
    /** Seconds */
    val duration: Double? = null,
    /** Progress of the current user, present when requested via `include=progress` */
    val userMediaProgress: AbsMediaProgress? = null,
)

@Serializable
data class AbsMediaProgress(
    val libraryItemId: String? = null,
    val episodeId: String? = null,
    /** Seconds */
    val duration: Double = 0.0,
    /** 0..1 */
    val progress: Double = 0.0,
    /** Seconds */
    val currentTime: Double = 0.0,
    val isFinished: Boolean = false,
    /** Unix millis */
    val lastUpdate: Long? = null,
)

@Serializable
data class AbsPlaySession(
    val id: String,
    val libraryItemId: String? = null,
    val episodeId: String? = null,
    val displayTitle: String? = null,
    val displayAuthor: String? = null,
    val duration: Double = 0.0,
    val currentTime: Double = 0.0,
    val audioTracks: List<AbsAudioTrack> = emptyList(),
)

@Serializable
data class AbsAudioTrack(
    val index: Int = 1,
    /** Seconds from the start of the whole item where this track begins */
    val startOffset: Double = 0.0,
    val duration: Double = 0.0,
    /** Server-relative URL, e.g. `/public/session/<id>/track/1` */
    val contentUrl: String,
    val mimeType: String? = null,
)

// ---- Request bodies ----

@Serializable
data class AbsLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AbsDeviceInfo(
    val clientName: String,
    val deviceId: String,
)

@Serializable
data class AbsPlayRequest(
    val deviceInfo: AbsDeviceInfo,
    val forceDirectPlay: Boolean = true,
    val forceTranscode: Boolean = false,
    val mediaPlayer: String = "exoplayer",
)

@Serializable
data class AbsSyncRequest(
    /** Seconds */
    val currentTime: Double,
    /** Seconds actually listened since the last sync */
    val timeListened: Double,
    val duration: Double? = null,
)

@Serializable
data class AbsProgressUpdate(
    val currentTime: Double,
    val duration: Double,
    val progress: Double,
    val isFinished: Boolean,
)

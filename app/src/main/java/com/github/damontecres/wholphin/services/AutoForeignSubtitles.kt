package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.api.MediaStream

/**
 * Automatic subtitles for foreign language audio (#46)
 *
 * When an item has neither a Slovak nor a Czech audio track, subtitles are turned on automatically
 * in the order Slovak, Czech, English and finally a track with an unknown language.
 */
object AutoForeignSubtitles {
    enum class Language {
        SLOVAK,
        CZECH,
        ENGLISH,
    }

    /**
     * The automatically chosen subtitle track and a human readable reason for logging
     */
    data class Choice(
        val stream: MediaStream,
        val reason: String,
    )

    private val slovakCodes = setOf("sk", "slo", "slk", "slovak", "slovencina", "slovenčina")
    private val czechCodes = setOf("cs", "cz", "cze", "ces", "czech", "cestina", "čeština")
    private val englishCodes = setOf("en", "eng", "english")
    private val unknownCodes = setOf("und", "unknown", "undetermined", "undefined", "mul", "zxx")

    /**
     * Normalizes ISO 639-1/639-2 language codes (and a few names) to a [Language]
     */
    fun normalizeLanguage(language: String?): Language? {
        val lang =
            language
                ?.trim()
                ?.lowercase()
                ?.substringBefore('-')
                ?.substringBefore('_')
                ?.takeIf { it.isNotBlank() }
                ?: return null
        return when (lang) {
            in slovakCodes -> Language.SLOVAK
            in czechCodes -> Language.CZECH
            in englishCodes -> Language.ENGLISH
            else -> null
        }
    }

    fun isUnknownLanguage(language: String?): Boolean = language.isNullOrBlank() || language.trim().lowercase() in unknownCodes

    private fun isUnknownTrack(stream: MediaStream): Boolean =
        isUnknownLanguage(stream.language) ||
            stream.title?.let {
                it.contains("External", ignoreCase = true) || it.contains("Unknown", ignoreCase = true)
            } == true

    private fun isForced(stream: MediaStream): Boolean =
        stream.isForced ||
            stream.title?.contains("forced", ignoreCase = true) == true

    private fun isSdh(stream: MediaStream): Boolean =
        stream.isHearingImpaired ||
            stream.title?.let {
                it.contains("SDH", ignoreCase = true) || it.contains("hearing impaired", ignoreCase = true)
            } == true

    /**
     * Lower is better: full subtitles, then SDH, then forced only
     */
    private fun kindRank(stream: MediaStream): Int =
        when {
            isForced(stream) -> 2
            isSdh(stream) -> 1
            else -> 0
        }

    private fun kindName(stream: MediaStream): String =
        when (kindRank(stream)) {
            0 -> "full"
            1 -> "SDH"
            else -> "forced"
        }

    private fun best(streams: List<MediaStream>): MediaStream? =
        streams
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<MediaStream>> { kindRank(it.value) }
                    .thenByDescending { it.value.isDefault }
                    .thenBy { it.index },
            ).firstOrNull()
            ?.value

    /**
     * Choose a subtitle track to enable automatically
     *
     * @param audioStreams all audio tracks of the media source
     * @param subtitleStreams all subtitle tracks of the media source
     * @param enabled whether the feature is turned on in the settings
     * @return the chosen track or null if the current behavior should be kept
     */
    fun choose(
        audioStreams: List<MediaStream>,
        subtitleStreams: List<MediaStream>,
        enabled: Boolean,
    ): Choice? {
        if (!enabled || audioStreams.isEmpty() || subtitleStreams.isEmpty()) return null
        val hasNativeAudio =
            audioStreams.any {
                val lang = normalizeLanguage(it.language)
                lang == Language.SLOVAK || lang == Language.CZECH
            }
        if (hasNativeAudio) return null
        val audioLangs = audioStreams.joinToString(",") { it.language ?: "?" }
        listOf(Language.SLOVAK, Language.CZECH, Language.ENGLISH).forEach { language ->
            best(subtitleStreams.filter { normalizeLanguage(it.language) == language })?.let {
                return Choice(
                    it,
                    "no SK/CZ audio ($audioLangs), ${language.name.lowercase()} ${kindName(it)} subtitles",
                )
            }
        }
        return best(subtitleStreams.filter { isUnknownTrack(it) })?.let {
            Choice(it, "no SK/CZ audio ($audioLangs), no SK/CZ/EN subtitles, unknown language ${kindName(it)} track")
        }
    }
}

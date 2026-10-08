package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * A ČSFD "TV tip dňa". [itemId] is set when it is in the user's library, otherwise [titles] can be used to find it in Seerr.
 */
data class CsfdTvTip(
    val csfdId: Int,
    val title: String,
    val year: Int?,
    val itemId: UUID?,
    val ratingPercent: Int?,
    val isSeries: Boolean,
    val titles: List<String>,
    val poster: String?,
)

/**
 * Gets the ČSFD "TV tipy dňa" from the Jellyfin ČSFD plugin (`/Csfd/TvTips`).
 *
 * The plugin returns the tips in the user's library (best rated first) followed by the best rated ones that are missing.
 */
@Singleton
class CsfdTvTipsService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
        private val seerrService: SeerrService,
    ) {
        /**
         * Items for the home row: tips in the library (playable) followed by the best rated missing tips that were
         * found in Seerr, which open the Seerr page to request them
         */
        suspend fun getRowItems(
            userId: UUID,
            useSeries: Boolean,
            limit: Int,
            missing: Int = 3,
        ): List<BaseItem> {
            val tips = getTips(limit, missing)
            val ids = tips.mapNotNull { it.itemId }
            val library =
                if (ids.isEmpty()) {
                    listOf()
                } else {
                    // The server does not keep the order of `ids`
                    val byId =
                        GetItemsRequestHandler
                            .execute(api, GetItemsRequest(userId = userId, ids = ids, fields = HomeItemFields))
                            .toBaseItems(useSeries)
                            .associateBy { it.id }
                    ids.mapNotNull { byId[it] }
                }
            val seerrActive = runCatching { seerrService.active.first() }.getOrDefault(false)
            val requestable =
                if (seerrActive) {
                    tips.filter { it.itemId == null }.mapNotNull { findInSeerr(it) }
                } else {
                    listOf()
                }
            return library + requestable
        }

        private suspend fun findInSeerr(tip: CsfdTvTip): BaseItem? {
            val wantedType = if (tip.isSeries) "tv" else "movie"
            for (query in (tip.titles.ifEmpty { listOf(tip.title) }).take(3)) {
                val match =
                    try {
                        seerrService.search(query).firstOrNull { result ->
                            val year = (result.releaseDate ?: result.firstAirDate)?.take(4)?.toIntOrNull()
                            result.mediaType == wantedType &&
                                (tip.year == null || year == null || abs(year - tip.year) <= 1)
                        }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Seerr search failed for %s", query)
                        null
                    } ?: continue
                val discover = seerrService.createDiscoverItem(match)
                return BaseItem(
                    data =
                        BaseItemDto(
                            id = UUID.nameUUIDFromBytes("csfd:${tip.csfdId}".toByteArray()),
                            type = if (tip.isSeries) BaseItemKind.SERIES else BaseItemKind.MOVIE,
                            name = tip.title,
                            productionYear = tip.year,
                            overview = discover.overview,
                            // Shows the ČSFD rating badge like the library items
                            communityRating = tip.ratingPercent?.div(10f),
                            providerIds = mapOf("Csfd" to tip.csfdId.toString()),
                        ),
                    imageUrlOverride = discover.posterUrl ?: tip.poster,
                    destinationOverride = discover.destination,
                )
            }
            Timber.i("ČSFD TV tip %s not found in Seerr", tip.title)
            return null
        }

        /**
         * Returns today's tips, or an empty list if the plugin is missing/unreachable
         */
        suspend fun getTips(
            limit: Int,
            missing: Int,
        ): List<CsfdTvTip> =
            withContext(Dispatchers.IO) {
                val baseUrl = api.baseUrl?.trimEnd('/') ?: return@withContext emptyList()
                try {
                    val request = Request.Builder().url("$baseUrl/Csfd/TvTips?limit=$limit&missing=$missing").build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            // 404 = plugin without TV tips support
                            Timber.w("ČSFD TV tips: HTTP %s", response.code)
                            return@withContext emptyList()
                        }
                        parseTips(response.body.string())
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "ČSFD TV tips failed")
                    emptyList()
                }
            }

        companion object {
            fun parseTips(json: String): List<CsfdTvTip> =
                (Json.parseToJsonElement(json) as? JsonArray)
                    .orEmpty()
                    .mapNotNull { element ->
                        val obj = element.jsonObject
                        val csfdId = obj.field("CsfdId")?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                        // GUIDs come without dashes
                        val itemId =
                            obj
                                .field("ItemId")
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?.toUUIDOrNull()
                        val inLibrary = obj.field("InLibrary")?.jsonPrimitive?.booleanOrNull ?: (itemId != null)
                        CsfdTvTip(
                            csfdId = csfdId,
                            title =
                                obj
                                    .field("Title")
                                    ?.jsonPrimitive
                                    ?.contentOrNull
                                    .orEmpty(),
                            year = obj.field("Year")?.jsonPrimitive?.intOrNull,
                            itemId = itemId.takeIf { inLibrary },
                            ratingPercent = obj.field("RatingPercent")?.jsonPrimitive?.intOrNull,
                            isSeries = obj.field("MediaType")?.jsonPrimitive?.contentOrNull == "tv",
                            titles =
                                runCatching {
                                    obj.field("Titles")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                }.getOrNull().orEmpty(),
                            poster = obj.field("Poster")?.jsonPrimitive?.contentOrNull,
                        )
                    }

            /** The server may emit PascalCase or camelCase */
            private fun JsonObject.field(name: String) = this[name] ?: this[name.replaceFirstChar { it.lowercase() }]
        }
    }

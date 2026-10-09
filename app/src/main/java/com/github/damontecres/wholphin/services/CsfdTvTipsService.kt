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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
    val photo: String? = null,
    val overview: String? = null,
    val genres: List<String> = listOf(),
    val durationMinutes: Int? = null,
)

/**
 * Talks to the Jellyfin ČSFD plugin: "TV tipy dňa" (`/Csfd/TvTips`) and the ČSFD best-of rankings (`/Csfd/Ranks`).
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
        // The plugin downloads 20 ranking pages from ČSFD on its first call of the week
        private val slowClient by lazy { okHttpClient.newBuilder().readTimeout(3, TimeUnit.MINUTES).build() }

        @Volatile
        private var ranks: Map<Int, Int>? = null

        /**
         * Items for the home row: tips in the library (playable) followed by the best rated missing tips that were
         * found in Seerr, which open the Seerr page to request them
         */
        suspend fun getRowItems(
            userId: UUID,
            useSeries: Boolean,
            limit: Int,
            missing: Int = 7,
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
            for (query in (tip.titles.ifEmpty { listOf(tip.title) }).take(4)) {
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
                // Title and plot from ČSFD, also on the Seerr page
                val discover =
                    seerrService
                        .createDiscoverItem(match)
                        .copy(csfdTitle = tip.title, csfdOverview = tip.overview)
                return BaseItem(
                    data =
                        BaseItemDto(
                            id = UUID.nameUUIDFromBytes("csfd:${tip.csfdId}".toByteArray()),
                            type = if (tip.isSeries) BaseItemKind.SERIES else BaseItemKind.MOVIE,
                            name = tip.title,
                            productionYear = tip.year,
                            overview = tip.overview ?: discover.overview,
                            genres = tip.genres,
                            runTimeTicks = tip.durationMinutes?.let { it * 60L * 10_000_000L },
                            // Shows the ČSFD rating badge like the library items
                            communityRating = tip.ratingPercent?.div(10f),
                            providerIds = mapOf("Csfd" to tip.csfdId.toString()),
                        ),
                    imageUrlOverride = discover.posterUrl ?: tip.poster,
                    backdropUrlOverride = discover.backDropUrl ?: tip.photo,
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
            // The first call of the day fetches the missing tips' details from ČSFD, which takes a while
        ): List<CsfdTvTip> = get("Csfd/TvTips?limit=$limit&missing=$missing", slowClient)?.let(::parseTips).orEmpty()

        /**
         * ČSFD id → position in the ČSFD best films/series rankings (top 1000 each); empty if the plugin is missing
         */
        suspend fun getRanks(): Map<Int, Int> =
            ranks ?: (get("Csfd/Ranks", slowClient)?.let(::parseRanks) ?: mapOf()).also {
                if (it.isNotEmpty()) ranks = it
            }

        private val trivia = ConcurrentHashMap<Int, List<String>>()

        /**
         * Interesting facts ("Zaujímavosti") about a film/series from ČSFD, best rated first; empty if none or the
         * plugin is missing/too old
         */
        suspend fun getTrivia(
            csfdId: Int,
            limit: Int = 4,
        ): List<String> =
            trivia[csfdId] ?: (
                get("Csfd/Trivia/$csfdId?limit=$limit", slowClient)
                    ?.let { json -> (json as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull } }
                    ?.also { trivia[csfdId] = it }
                    .orEmpty()
            )

        @Volatile
        private var myRatings: MutableMap<Int, Int>? = null

        /**
         * My ČSFD ratings (ČSFD id → 0-5 stars, 0 = "odpad") read by the plugin from the profile set in its settings
         */
        suspend fun getMyRatings(): Map<Int, Int> =
            myRatings ?: (get("Csfd/MyRatings", slowClient)?.let(::parseRanks)?.toMutableMap() ?: mutableMapOf()).also {
                if (it.isNotEmpty()) myRatings = it
            }

        /**
         * Rate on ČSFD through the plugin's account. Returns null on success, otherwise an error message.
         */
        suspend fun rate(
            csfdId: Int,
            stars: Int,
        ): String? =
            withContext(Dispatchers.IO) {
                val baseUrl = api.baseUrl?.trimEnd('/') ?: return@withContext "No server"
                try {
                    val request =
                        Request
                            .Builder()
                            .url("$baseUrl/Csfd/MyRatings/$csfdId?stars=$stars")
                            .post(ByteArray(0).toRequestBody())
                            .build()
                    slowClient.newCall(request).execute().use { response ->
                        val obj = runCatching { Json.parseToJsonElement(response.body.string()).jsonObject }.getOrNull()
                        if (response.isSuccessful && obj?.field("Ok")?.jsonPrimitive?.booleanOrNull == true) {
                            myRatings?.set(csfdId, stars)
                            null
                        } else {
                            obj?.string("Message") ?: "HTTP ${response.code}"
                        }
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "ČSFD rating failed")
                    ex.localizedMessage ?: ex.toString()
                }
            }

        private suspend fun get(
            path: String,
            client: OkHttpClient,
        ): JsonElement? =
            withContext(Dispatchers.IO) {
                val baseUrl = api.baseUrl?.trimEnd('/') ?: return@withContext null
                try {
                    client.newCall(Request.Builder().url("$baseUrl/$path").build()).execute().use { response ->
                        if (!response.isSuccessful) {
                            // 404 = plugin too old for this endpoint
                            Timber.w("ČSFD plugin %s: HTTP %s", path, response.code)
                            null
                        } else {
                            Json.parseToJsonElement(response.body.string())
                        }
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "ČSFD plugin %s failed", path)
                    null
                }
            }

        companion object {
            fun parseTips(json: String): List<CsfdTvTip> = parseTips(Json.parseToJsonElement(json))

            fun parseTips(json: JsonElement): List<CsfdTvTip> =
                (json as? JsonArray)
                    .orEmpty()
                    .mapNotNull { element ->
                        val obj = element.jsonObject
                        val csfdId = obj.int("CsfdId") ?: return@mapNotNull null
                        // GUIDs come without dashes
                        val itemId = obj.string("ItemId")?.toUUIDOrNull()
                        val inLibrary = obj.field("InLibrary")?.jsonPrimitive?.booleanOrNull ?: (itemId != null)
                        CsfdTvTip(
                            csfdId = csfdId,
                            title = obj.string("Title").orEmpty(),
                            year = obj.int("Year"),
                            itemId = itemId.takeIf { inLibrary },
                            ratingPercent = obj.int("RatingPercent"),
                            isSeries = obj.string("MediaType") == "tv",
                            titles = obj.strings("Titles"),
                            poster = obj.string("Poster"),
                            photo = obj.string("Photo"),
                            overview = obj.string("Overview"),
                            genres = obj.strings("Genres"),
                            durationMinutes = obj.int("DurationMinutes"),
                        )
                    }

            fun parseRanks(json: JsonElement): Map<Int, Int> =
                (json as? JsonObject)
                    .orEmpty()
                    .mapNotNull { (id, rank) -> id.toIntOrNull()?.let { it to (rank.jsonPrimitive.intOrNull ?: return@mapNotNull null) } }
                    .toMap()

            /** The server may emit PascalCase or camelCase */
            private fun JsonObject.field(name: String) = this[name] ?: this[name.replaceFirstChar { it.lowercase() }]

            private fun JsonObject.string(name: String) = field(name)?.jsonPrimitive?.contentOrNull

            private fun JsonObject.int(name: String) = field(name)?.jsonPrimitive?.intOrNull

            private fun JsonObject.strings(name: String) =
                runCatching { field(name)?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty()
        }
    }

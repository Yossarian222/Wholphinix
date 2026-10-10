package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    /** Small poster from the ČSFD TV tips page, the last resort when neither Seerr nor the plugin has a poster */
    val thumbnail: String? = null,
)

/**
 * Talks to the Jellyfin ČSFD plugin: "TV tipy dňa" (`/Csfd/TvTips`), the user's watchlist "Chcem vidieť" (`/Csfd/Watchlist`)
 * and the ČSFD best-of rankings (`/Csfd/Ranks`).
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
        private val serverRepository: ServerRepository,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        // The plugin downloads 20 ranking pages from ČSFD on its first call of the week
        private val slowClient by lazy { okHttpClient.newBuilder().readTimeout(3, TimeUnit.MINUTES).build() }

        /**
         * What is cached for one user on one server, so switching the user or server does not show the old ones
         */
        private class Caches(
            val key: Pair<UUID, UUID>?,
        ) {
            @Volatile
            var ranks: Map<Int, Int>? = null

            @Volatile
            var ranksJob: Deferred<Map<Int, Int>>? = null

            /** Updated by [rate] while it may be read */
            @Volatile
            var myRatings: ConcurrentHashMap<Int, Int>? = null

            val trivia = ConcurrentHashMap<Int, List<String>>()
        }

        @Volatile
        private var currentCaches = Caches(null)

        private fun caches(): Caches {
            val key = serverRepository.current.value?.let { it.server.id to it.user.id }
            currentCaches.let { if (it.key == key) return it }
            return synchronized(this) {
                currentCaches.takeIf { it.key == key } ?: Caches(key).also { currentCaches = it }
            }
        }

        /**
         * Items for the home row: tips in the library (playable) followed by the best rated missing tips that were
         * found in Seerr, which open the Seerr page to request them.
         *
         * The first call of the day can take minutes (the plugin fetches the missing tips from ČSFD), so this gives up
         * after [ROW_TIMEOUT] with an empty row instead of holding up the home page. The request keeps running in the
         * background, so the plugin caches the tips and the next load is fast.
         */
        suspend fun getRowItems(
            userId: UUID,
            useSeries: Boolean,
            limit: Int,
            missing: Int = 7,
        ): List<BaseItem> = rowItems("TV tips", userId, useSeries) { getTips(limit, missing) }

        /**
         * Items for the "Chcem vidieť (ČSFD)" home row: the user's ČSFD watchlist titles in the library (playable, in the
         * ČSFD order) followed by missing ones found in Seerr. Same timeout as [getRowItems]; empty if the plugin has no
         * `/Csfd/Watchlist` endpoint yet (404). The full page passes a longer [timeout], since it has nothing else to show.
         */
        suspend fun getWatchlistRowItems(
            userId: UUID,
            useSeries: Boolean,
            limit: Int,
            missing: Int = 10,
            timeout: Duration = ROW_TIMEOUT,
        ): List<BaseItem> = rowItems("watchlist", userId, useSeries, timeout) { getWatchlist(limit, missing) }

        /**
         * Items for the seasonal home row (eg "🎃 Na Halloween"): the plugin's curated titles for the holiday [event]
         * (`newyear`, `valentine`, `easter`, `halloween`, `nicholas`, `christmas`), the ones in the library first, then
         * missing ones found in Seerr. Same timeout as [getRowItems]; empty if the plugin has no `/Csfd/Seasonal`
         * endpoint yet (404).
         */
        suspend fun getSeasonalRowItems(
            event: String,
            userId: UUID,
            useSeries: Boolean,
            limit: Int = 20,
            missing: Int = 20,
        ): List<BaseItem> = rowItems("seasonal $event", userId, useSeries) { getSeasonal(event, limit, missing) }

        /**
         * The plugin's curated titles for a holiday, same format as [getTips]. Empty if the plugin is missing/too old.
         */
        suspend fun getSeasonal(
            event: String,
            limit: Int,
            missing: Int,
        ): List<CsfdTvTip> = get("Csfd/Seasonal?event=$event&limit=$limit&missing=$missing", slowClient)?.let(::parseTips).orEmpty()

        private suspend fun rowItems(
            name: String,
            userId: UUID,
            useSeries: Boolean,
            timeout: Duration = ROW_TIMEOUT,
            fetch: suspend () -> List<CsfdTvTip>,
        ): List<BaseItem> {
            val load = scope.async { loadRowItems(userId, useSeries, fetch()) }
            return withTimeoutOrNull(timeout) { load.await() }
                ?: listOf<BaseItem>().also { Timber.i("ČSFD %s took too long, showing an empty row", name) }
        }

        private suspend fun loadRowItems(
            userId: UUID,
            useSeries: Boolean,
            tips: List<CsfdTvTip>,
        ): List<BaseItem> {
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
                    // In parallel, each tip may need several searches; awaitAll keeps the order
                    coroutineScope {
                        tips
                            .filter { it.itemId == null }
                            .map { async { findInSeerr(it) } }
                            .awaitAll()
                            .filterNotNull()
                    }
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
                    imageUrlOverride = discover.posterUrl ?: tip.poster ?: tip.thumbnail,
                    backdropUrlOverride = discover.backDropUrl ?: tip.photo,
                    destinationOverride = discover.destination,
                    // Seerr may know it from Jellyfin even though the plugin did not match it
                    inLibrary = discover.jellyfinItemId != null,
                )
            }
            Timber.i("ČSFD title %s not found in Seerr", tip.title)
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
         * The user's ČSFD watchlist ("Chcem vidieť") from the profile set in the plugin, same format as [getTips]:
         * titles in the library first, then up to [missing] missing ones. Empty if the plugin is missing/too old.
         */
        suspend fun getWatchlist(
            limit: Int,
            missing: Int,
        ): List<CsfdTvTip> = get("Csfd/Watchlist?limit=$limit&missing=$missing", slowClient)?.let(::parseTips).orEmpty()

        /**
         * ČSFD id → position in the ČSFD best films/series rankings (top 1000 each); empty if the plugin is missing
         */
        suspend fun getRanks(): Map<Int, Int> {
            val caches = caches()
            caches.ranks?.let { return it }
            // Shared and outside of the caller, so a caller giving up does not abort the slow first download
            val job =
                synchronized(caches) {
                    caches.ranksJob?.takeIf { it.isActive }
                        ?: scope
                            .async {
                                (get("Csfd/Ranks", slowClient)?.let(::parseRanks) ?: mapOf()).also {
                                    if (it.isNotEmpty()) caches.ranks = it
                                }
                            }.also { caches.ranksJob = it }
                }
            return job.await()
        }

        /**
         * Interesting facts ("Zaujímavosti") about a film/series from ČSFD, best rated first; empty if none or the
         * plugin is missing/too old
         */
        suspend fun getTrivia(
            csfdId: Int,
            limit: Int = 4,
        ): List<String> {
            val trivia = caches().trivia
            return trivia[csfdId] ?: (
                get("Csfd/Trivia/$csfdId?limit=$limit", slowClient)
                    ?.let { json -> (json as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }
                    ?.also { trivia[csfdId] = it }
                    .orEmpty()
            )
        }

        /**
         * My ČSFD ratings (ČSFD id → 0-5 stars, 0 = "odpad") read by the plugin from the profile set in its settings
         */
        suspend fun getMyRatings(): Map<Int, Int> {
            val caches = caches()
            return caches.myRatings ?: ConcurrentHashMap(get("Csfd/MyRatings", slowClient)?.let(::parseRanks).orEmpty()).also {
                if (it.isNotEmpty()) caches.myRatings = it
            }
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
                val caches = caches()
                try {
                    val request =
                        Request
                            .Builder()
                            .url("$baseUrl/Csfd/MyRatings/$csfdId?stars=$stars")
                            .post(ByteArray(0).toRequestBody())
                            .build()
                    slowClient.newCall(request).execute().use { response ->
                        val obj = runCatching { Json.parseToJsonElement(response.body.string()) as? JsonObject }.getOrNull()
                        if (response.isSuccessful && (obj?.field("Ok") as? JsonPrimitive)?.booleanOrNull == true) {
                            caches.myRatings?.set(csfdId, stars)
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
            private val ROW_TIMEOUT = 12.seconds

            fun parseTips(json: String): List<CsfdTvTip> = parseTips(Json.parseToJsonElement(json))

            fun parseTips(json: JsonElement): List<CsfdTvTip> =
                (json as? JsonArray)
                    .orEmpty()
                    .mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        val csfdId = obj.int("CsfdId") ?: return@mapNotNull null
                        // GUIDs come without dashes
                        val itemId = obj.string("ItemId")?.toUUIDOrNull()
                        val inLibrary = (obj.field("InLibrary") as? JsonPrimitive)?.booleanOrNull ?: (itemId != null)
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
                            thumbnail = obj.string("Thumbnail")?.let(::csfdImageUrl),
                        )
                    }

            private val CSFD_RESIZED = Regex("/cache/resized/w\\d+(h\\d+)?/")

            /**
             * ČSFD image URL that can be loaded: protocol relative URLs (`//image.pmgstatic.com/...`) get https and the
             * small resized thumbnails (eg `/cache/resized/w60h85/`) are asked for in the poster size (`w420`)
             */
            fun csfdImageUrl(url: String): String? {
                val trimmed = url.trim().takeIf { it.isNotEmpty() } ?: return null
                val absolute = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
                return absolute.replace(CSFD_RESIZED, "/cache/resized/w420/")
            }

            fun parseRanks(json: JsonElement): Map<Int, Int> =
                (json as? JsonObject)
                    .orEmpty()
                    .mapNotNull { (id, rank) ->
                        id.toIntOrNull()?.let {
                            it to
                                ((rank as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null)
                        }
                    }.toMap()

            /** The server may emit PascalCase or camelCase */
            private fun JsonObject.field(name: String) = this[name] ?: this[name.replaceFirstChar { it.lowercase() }]

            private fun JsonObject.string(name: String) = (field(name) as? JsonPrimitive)?.contentOrNull

            private fun JsonObject.int(name: String) = (field(name) as? JsonPrimitive)?.intOrNull

            private fun JsonObject.strings(name: String) =
                (field(name) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        }
    }

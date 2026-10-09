package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.ClaudeFrequency
import com.github.damontecres.wholphin.preferences.ClaudePreferences
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * "Claude on the couch": short, funny or interesting remarks shown with [TvMessageService] while watching
 * (a few minutes into a film, during a longer pause) and a tip for tonight on an idle home page.
 *
 * Calls the Anthropic API directly from the TV with the user's own key ([ClaudePreferences]). Only the film's
 * metadata (title, year, people, plot, ČSFD trivia) is sent, never server URLs or tokens. Nothing is sent when the
 * feature is off or no key is set; every error is only logged.
 */
@Singleton
class ClaudeCompanionService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val navigationManager: NavigationManager,
        private val csfdTvTipsService: CsfdTvTipsService,
        private val tvMessageService: TvMessageService,
        private val claudeApiClient: ClaudeApiClient,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        // Playback state, only touched from the main thread (player callbacks) and the jobs below
        @Volatile
        private var currentItemId: UUID? = null
        private val playing = MutableStateFlow(false)

        @Volatile
        private var remarksForItem = 0
        private val saidForItem = mutableListOf<String>()

        @Volatile
        private var lastRemarkAt: TimeSource.Monotonic.ValueTimeMark? = null

        @Volatile
        private var startJob: Job? = null

        @Volatile
        private var pauseJob: Job? = null

        // Home page idle tip
        @Volatile
        private var idleJob: Job? = null

        @Volatile
        private var lastHomeTipAt: TimeSource.Monotonic.ValueTimeMark? = null

        /** Settings if the companion may talk at all, else null */
        private suspend fun activeSettings(): ClaudePreferences? {
            val prefs = userPreferencesService.getCurrent().appPreferences.claudePreferences
            return prefs.takeIf {
                it.enabled && it.apiKey.isNotBlank() && it.frequency in ACTIVE_FREQUENCIES
            }
        }

        // ---- Playback ----

        /**
         * A film or an episode started playing (called for each item, also the next episode)
         */
        @Synchronized
        fun onPlaybackStarted(
            itemId: UUID,
            type: BaseItemKind,
        ) {
            if (type != BaseItemKind.MOVIE && type != BaseItemKind.EPISODE) return
            if (itemId == currentItemId) return
            cancelPlaybackJobs()
            currentItemId = itemId
            // It is about to play; a pause is reported by onPlayingChanged
            playing.value = true
            remarksForItem = 0
            synchronized(saidForItem) { saidForItem.clear() }
            startJob =
                scope.launch {
                    if (activeSettings() == null) return@launch
                    delay(Random.nextLong(START_DELAY_MIN.inWholeSeconds, START_DELAY_MAX.inWholeSeconds).seconds)
                    // Paused at that moment: wait until it plays again
                    playing.first { it }
                    remark(itemId, Occasion.START)
                }
        }

        /** The player started or stopped playing (pause) */
        @Synchronized
        fun onPlayingChanged(isPlaying: Boolean) {
            playing.value = isPlaying
            pauseJob?.cancel()
            val itemId = currentItemId ?: return
            if (!isPlaying) {
                pauseJob =
                    scope.launch {
                        delay(PAUSE_DELAY)
                        val settings = activeSettings() ?: return@launch
                        val chance = if (settings.frequency == ClaudeFrequency.CLAUDE_OFTEN) 1.0 else 1.0 / 3
                        if (Random.nextDouble() < chance) {
                            remark(itemId, Occasion.PAUSE)
                        }
                    }
            }
        }

        /** The player was closed */
        @Synchronized
        fun onPlaybackStopped() {
            cancelPlaybackJobs()
            currentItemId = null
            playing.value = false
            onUserActivity()
        }

        private fun cancelPlaybackJobs() {
            startJob?.cancel()
            pauseJob?.cancel()
        }

        private enum class Occasion { START, PAUSE }

        private suspend fun remark(
            itemId: UUID,
            occasion: Occasion,
        ) {
            if (itemId != currentItemId || remarksForItem >= MAX_REMARKS_PER_ITEM) return
            if (lastRemarkAt?.let { it.elapsedNow() < MIN_GAP } == true) return
            val settings = activeSettings() ?: return
            try {
                val facts = describeItem(itemId) ?: return
                val said = synchronized(saidForItem) { saidForItem.toList() }
                val prompt =
                    buildString {
                        when (occasion) {
                            Occasion.START -> {
                                appendLine(
                                    "Práve sme si pustili toto a beží to pár minút. Napíš jednu krátku hlášku: " +
                                        "vtipnú poznámku alebo zaujímavosť o filme, tvorcoch alebo hercoch.",
                                )
                            }

                            Occasion.PAUSE -> {
                                appendLine(
                                    "Dal som si pauzu (asi si idem po pivo). Povedz mi jednu krátku zaujímavosť " +
                                        "k tomuto, bez spoilerov.",
                                )
                            }
                        }
                        if (said.isNotEmpty()) {
                            appendLine("Toto si už povedal, neopakuj sa: ${said.joinToString(" | ")}")
                        }
                        appendLine()
                        appendLine("Podklady:")
                        append(facts)
                    }
                val text = ask(settings, prompt) ?: return
                // The film may have changed while waiting for the answer
                if (itemId != currentItemId) return
                remarksForItem++
                synchronized(saidForItem) { saidForItem.add(text) }
                show(text)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Claude companion remark failed")
            }
        }

        /** Metadata of a film/episode for the prompt; nothing that identifies the server */
        private suspend fun describeItem(itemId: UUID): String? {
            val item = api.userLibraryApi.getItem(itemId).content
            val series =
                item.seriesId?.takeIf { item.type == BaseItemKind.EPISODE }?.let { seriesId ->
                    runCatching { api.userLibraryApi.getItem(seriesId).content }.getOrNull()
                }
            val csfdId = csfdId(item) ?: series?.let { csfdId(it) }
            val trivia =
                csfdId
                    ?.let { runCatching { csfdTvTipsService.getTrivia(it, limit = 4) }.getOrNull() }
                    .orEmpty()
            val people = (item.people.orEmpty() + series?.people.orEmpty())
            val directors =
                people
                    .filter { it.type == PersonKind.DIRECTOR }
                    .mapNotNull { it.name }
                    .distinct()
                    .take(3)
            val actors =
                people
                    .filter { it.type == PersonKind.ACTOR }
                    .mapNotNull { p -> p.name?.let { name -> p.role?.takeIf { it.isNotBlank() }?.let { "$name ($it)" } ?: name } }
                    .distinct()
                    .take(6)
            return buildString {
                if (item.type == BaseItemKind.EPISODE) {
                    appendLine("Seriál: ${item.seriesName ?: series?.name}")
                    appendLine("Epizóda: S${item.parentIndexNumber ?: "?"}E${item.indexNumber ?: "?"} ${item.name.orEmpty()}")
                } else {
                    appendLine("Film: ${item.name}")
                }
                (item.productionYear ?: series?.productionYear)?.let { appendLine("Rok: $it") }
                (item.genres ?: series?.genres)?.takeIf { it.isNotEmpty() }?.let { appendLine("Žánre: ${it.joinToString()}") }
                (item.communityRating ?: series?.communityRating)?.let { appendLine("Hodnotenie ČSFD: ${Math.round(it * 10)} %") }
                if (directors.isNotEmpty()) appendLine("Réžia: ${directors.joinToString()}")
                if (actors.isNotEmpty()) appendLine("Hrajú: ${actors.joinToString()}")
                (item.overview ?: series?.overview)?.let { appendLine("Popis: ${it.take(OVERVIEW_LENGTH)}") }
                if (trivia.isNotEmpty()) {
                    appendLine("Zaujímavosti z ČSFD:")
                    trivia.forEach { appendLine("- ${it.take(TRIVIA_LENGTH)}") }
                }
            }
        }

        private fun csfdId(item: BaseItemDto): Int? =
            item.providerIds
                ?.entries
                ?.firstOrNull { it.key.equals("Csfd", ignoreCase = true) }
                ?.value
                ?.toIntOrNull()

        // ---- Home page ----

        /** Any key press (and the app coming to the front): restarts the idle timer for the home page tip */
        fun onUserActivity() {
            idleJob?.cancel()
            idleJob =
                scope.launch {
                    delay(HOME_IDLE)
                    homeTip()
                }
        }

        /** The app went to the background */
        fun onAppHidden() {
            idleJob?.cancel()
        }

        private suspend fun homeTip() {
            if (currentItemId != null) return
            if (lastHomeTipAt?.let { it.elapsedNow() < HOME_TIP_INTERVAL } == true) return
            val onHome = withContext(WholphinDispatchers.Main) { navigationManager.backStack.lastOrNull() is Destination.Home }
            if (!onHome) return
            val settings = activeSettings() ?: return
            // Sometimes only every other chance
            if (settings.frequency == ClaudeFrequency.CLAUDE_SOMETIMES && Random.nextBoolean()) {
                lastHomeTipAt = TimeSource.Monotonic.markNow()
                return
            }
            lastHomeTipAt = TimeSource.Monotonic.markNow()
            try {
                val userId =
                    serverRepository.current.value
                        ?.user
                        ?.id ?: return
                val movies =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                recursive = true,
                                isPlayed = false,
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = HOME_TIP_MOVIES,
                                fields = listOf(ItemFields.GENRES),
                                enableTotalRecordCount = false,
                            ),
                        ).content.items
                if (movies.isEmpty()) return
                val list =
                    movies.joinToString("\n") { m ->
                        listOfNotNull(
                            m.name,
                            m.productionYear?.toString(),
                            m.genres
                                ?.take(3)
                                ?.takeIf { it.isNotEmpty() }
                                ?.joinToString("/"),
                            m.communityRating?.let { "ČSFD ${Math.round(it * 10)} %" },
                        ).joinToString(", ", prefix = "- ")
                    }
                val now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                val prompt =
                    "Je $now, sedím na domovskej obrazovke a neviem, čo pozerať. " +
                        "Odporuč mi jeden film z tohto zoznamu nevidených filmov z mojej knižnice (názov a rok) a krátko, " +
                        "vtipne povedz prečo práve ten.\n\n$list"
                ask(settings, prompt)?.let { show(it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Claude companion home tip failed")
            }
        }

        // ---- Common ----

        private suspend fun ask(
            settings: ClaudePreferences,
            prompt: String,
        ): String? =
            claudeApiClient
                .complete(
                    apiKey = settings.apiKey,
                    model = settings.model,
                    system = SYSTEM_PROMPT,
                    prompt = prompt,
                    maxTokens = MAX_TOKENS,
                )?.let(::clean)

        private fun show(text: String) {
            lastRemarkAt = TimeSource.Monotonic.markNow()
            tvMessageService.show(title = "Claude", text = text, timeoutMs = displayMs(text))
        }

        companion object {
            private val ACTIVE_FREQUENCIES = setOf(ClaudeFrequency.CLAUDE_SOMETIMES, ClaudeFrequency.CLAUDE_OFTEN)
            private val START_DELAY_MIN = 3.minutes
            private val START_DELAY_MAX = 8.minutes
            private val PAUSE_DELAY = 20.seconds
            private val MIN_GAP = 2.minutes
            private const val MAX_REMARKS_PER_ITEM = 3
            private val HOME_IDLE = 2.minutes
            private val HOME_TIP_INTERVAL = 30.minutes
            private const val HOME_TIP_MOVIES = 20
            private const val MAX_TOKENS = 150
            private const val MAX_LENGTH = 300
            private const val OVERVIEW_LENGTH = 700
            private const val TRIVIA_LENGTH = 400

            /** Long enough to read: ~60 ms per character, 8-20 s */
            fun displayMs(text: String): Long = (text.length * 60L).coerceIn(8_000L, 20_000L)

            /** Drops wrapping quotes and keeps it short */
            fun clean(text: String): String? {
                val trimmed =
                    text
                        .trim()
                        .removeSurrounding("\"")
                        .removeSurrounding("„", "“")
                        .trim()
                if (trimmed.isEmpty()) return null
                return if (trimmed.length <= MAX_LENGTH) trimmed else trimmed.take(MAX_LENGTH - 1).trimEnd() + "…"
            }

            const val SYSTEM_PROMPT =
                """Si Claude a sedíš s kamarátom na gauči pri telke. Tvoje hlášky sa mu zobrazia ako malá bublina v rohu obrazovky v appke Wholphinix, často počas filmu, takže ho nesmú rušiť dlhým textom.

Ako píšeš:
- Po slovensky, tykáš, uvoľnene a s humorom ako dobrý kamarát. Vtipne, ale nie trápne, žiadne lacné vtipy ani poučovanie.
- Najviac 2 krátke vety, spolu do 200 znakov. Najviac jeden emoji, žiadne hashtagy.
- Odpovedz iba samotnou hláškou, bez úvodzoviek, bez oslovenia a bez vysvetľovania.
- Nepýtaj sa otázky, na ktoré treba odpovedať. Kamarát ti nemôže odpísať.
- Neopakuj stále rovnaký začiatok (napríklad „Vedel si, že…“).

Obsah:
- Nikdy neprezrádzaj dej: žiadne zvraty, odhalenia ani koniec, ani pri starých a známych filmoch. Môžeš hovoriť o natáčaní, hercoch, tvorcoch, hudbe, ohlase či dobe vzniku.
- Fakty ber z dodaných podkladov alebo z toho, čo je všeobecne známe a isté. Keď si nie si istý, radšej zažartuj, ako by si si mal niečo vymyslieť. Čísla, mená a dátumy si nevymýšľaj.
- Podklady (popis, zaujímavosti z ČSFD) môžu byť po česky. Ty píš vždy po slovensky.
- Pri odporúčaní na večer vyber len film zo zoznamu, ktorý dostaneš, a napíš jeho názov aj rok."""
        }
    }

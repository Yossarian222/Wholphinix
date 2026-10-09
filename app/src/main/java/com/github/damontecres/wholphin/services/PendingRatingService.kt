package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * A finished movie waiting for its ČSFD rating
 */
data class PendingRating(
    val itemId: UUID,
    val csfdId: Int,
    val title: String,
)

/**
 * Asks for a ČSFD rating after a movie has been watched to the end.
 *
 * The player reports the movie when it is left, the root composable shows the prompt once the player is gone.
 */
@Singleton
class PendingRatingService
    @Inject
    constructor(
        private val api: ApiClient,
        private val csfdTvTipsService: CsfdTvTipsService,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        private val _pending = MutableStateFlow<PendingRating?>(null)
        val pending: StateFlow<PendingRating?> = _pending

        // Items already asked about (rated or dismissed) in this app session
        private val handled = ConcurrentHashMap.newKeySet<UUID>()

        /**
         * Called when the player is left.
         *
         * @param item the movie that was playing
         * @param watched whether it ended or was left after at least [WATCHED_FRACTION] of its length
         */
        fun onPlaybackLeft(
            item: BaseItem,
            watched: Boolean,
        ) {
            if (item.type != BaseItemKind.MOVIE || item.id in handled) return
            val csfdId = item.csfdId ?: return
            val wasPlayed = item.played
            scope.launch(ExceptionHandler()) {
                val finished =
                    watched ||
                        // The server may use its own threshold for marking an item played,
                        // give the stop report a moment to arrive first
                        (!wasPlayed && playedOnServer(item.id))
                if (!finished) return@launch
                // An empty map means the plugin has no ČSFD profile to read, so nothing could be rated
                val myRatings = csfdTvTipsService.getMyRatings()
                if (myRatings.isEmpty() || myRatings.containsKey(csfdId)) return@launch
                if (handled.add(item.id)) {
                    Timber.i("Asking for a ČSFD rating of %s (%s)", item.id, csfdId)
                    _pending.update { PendingRating(item.id, csfdId, item.name ?: "") }
                }
            }
        }

        /** The prompt was answered or dismissed */
        fun clear() {
            _pending.update { null }
        }

        private suspend fun playedOnServer(itemId: UUID): Boolean {
            delay(2.seconds)
            return try {
                api.userLibraryApi
                    .getItem(itemId)
                    .content.userData
                    ?.played == true
            } catch (ex: Exception) {
                Timber.w(ex, "Could not check whether %s was played", itemId)
                false
            }
        }

        companion object {
            const val WATCHED_FRACTION = 0.9
        }
    }

/** The ČSFD id from the ČSFD metadata plugin, if any */
val BaseItem.csfdId: Int?
    get() =
        data.providerIds
            ?.entries
            ?.firstOrNull { it.key.equals("Csfd", ignoreCase = true) }
            ?.value
            ?.toIntOrNull()

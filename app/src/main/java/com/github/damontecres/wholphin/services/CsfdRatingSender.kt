package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Outcome of sending "Moje hodnotenie" to ČSFD.
 *
 * @param stars what was sent
 * @param revertTo on failure, the rating shown before the user started changing it (null = unrated)
 * @param error null on success, otherwise the message shown to the user
 */
data class CsfdRatingResult(
    val csfdId: Int,
    val stars: Int,
    val revertTo: Int?,
    val error: String?,
)

/**
 * Sends ČSFD ratings from the detail page ("Moje hodnotenie") in the application scope, so leaving the page does not
 * cancel the request, and confirms the result with a toast (application context, works after the page is gone).
 *
 * Rapid changes of the same title (e.g. stepping over the stars) are debounced, only the last choice is sent after
 * [DEBOUNCE] without a further change. Requests are sent one at a time at least [MIN_INTERVAL] apart, because the
 * plugin refuses ratings sent less than 1 s apart (HTTP 429 "Príliš rýchlo za sebou").
 */
@Singleton
class CsfdRatingSender
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val csfdTvTipsService: CsfdTvTipsService,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        /** ČSFD ids waiting for the debounce or being sent */
        private val _sending = MutableStateFlow<Set<Int>>(setOf())
        val sending: StateFlow<Set<Int>> = _sending

        private val _results = MutableSharedFlow<CsfdRatingResult>(extraBufferCapacity = 16)
        val results: SharedFlow<CsfdRatingResult> = _results

        /** Shows the error, or the confirmation when null; replaceable in tests (no Android resources there) */
        internal var showMessage: suspend (String?) -> Unit = {
            showToast(context, it ?: context.getString(R.string.csfd_rating_prompt_sent))
        }

        private val lock = Any()

        // All guarded by lock
        private val debounceJobs = mutableMapOf<Int, Job>()
        private val latestStars = mutableMapOf<Int, Int>()
        private val generations = mutableMapOf<Int, Int>()
        private val revertTo = mutableMapOf<Int, Int?>()

        private val sendMutex = Mutex()
        private var lastSentAtNanos: Long? = null

        /** The choice waiting to be sent for [csfdId], if any, so a reopened page shows it instead of the old rating */
        fun pendingStars(csfdId: Int): Int? = synchronized(lock) { latestStars[csfdId] }

        /**
         * Rate [csfdId] with [stars] (0 = "odpad"); [previous] is the rating shown before this change
         */
        fun rate(
            csfdId: Int,
            stars: Int,
            previous: Int?,
        ) {
            synchronized(lock) {
                debounceJobs.remove(csfdId)?.cancel()
                // Back to the rating before the first of several quick changes, not to an unsent one
                if (!revertTo.containsKey(csfdId)) revertTo[csfdId] = previous
                latestStars[csfdId] = stars
                val generation = (generations[csfdId] ?: 0) + 1
                generations[csfdId] = generation
                _sending.update { it + csfdId }
                Timber.d("ČSFD rating %s → %s queued", csfdId, stars)
                // LAZY so the job is registered before it can look itself up
                val job =
                    scope.launch(ExceptionHandler(), start = CoroutineStart.LAZY) {
                        delay(DEBOUNCE)
                        // From now on a newer choice does not cancel this request, it is sent after it
                        synchronized(lock) {
                            if (debounceJobs[csfdId] === coroutineContext[Job]) debounceJobs.remove(csfdId)
                        }
                        send(csfdId, stars, generation)
                    }
                debounceJobs[csfdId] = job
                job.start()
            }
        }

        private suspend fun send(
            csfdId: Int,
            stars: Int,
            generation: Int,
        ) {
            val error =
                sendMutex.withLock {
                    lastSentAtNanos?.let {
                        val wait = MIN_INTERVAL - (System.nanoTime() - it).nanosToMillis().milliseconds
                        if (wait.isPositive()) delay(wait)
                    }
                    Timber.i("Sending ČSFD rating %s → %s", csfdId, stars)
                    try {
                        csfdTvTipsService.rate(csfdId, stars)
                    } finally {
                        lastSentAtNanos = System.nanoTime()
                    }
                }
            val result =
                synchronized(lock) {
                    val latest = generations[csfdId] == generation
                    if (!latest) {
                        // A newer choice follows and decides the outcome; if this one reached ČSFD it is what to go back to
                        if (error == null) revertTo[csfdId] = stars
                        Timber.i("ČSFD rating %s → %s: %s, a newer choice follows", csfdId, stars, error ?: "ok")
                        null
                    } else {
                        val result = CsfdRatingResult(csfdId, stars, revertTo[csfdId], error)
                        revertTo.remove(csfdId)
                        latestStars.remove(csfdId)
                        _sending.update { it - csfdId }
                        result
                    }
                } ?: return
            if (result.error == null) {
                Timber.i("ČSFD rating %s → %s sent", csfdId, stars)
            } else {
                Timber.w("ČSFD rating %s → %s failed: %s", csfdId, stars, result.error)
            }
            _results.emit(result)
            showMessage(result.error)
        }

        companion object {
            val DEBOUNCE = 600.milliseconds

            /** The plugin's throttle is 1 s, with some margin */
            val MIN_INTERVAL = 1100.milliseconds

            private fun Long.nanosToMillis() = this / 1_000_000
        }
    }

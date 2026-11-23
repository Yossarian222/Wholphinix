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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    /** Failed, but kept in the queue and sent again later, so the stars stay */
    val queued: Boolean = false,
)

/** A rating that failed to send and waits for another attempt; [attempts] failed so far */
data class QueuedCsfdRating(
    val stars: Int,
    val attempts: Int,
)

/** Keeps the ratings waiting for another attempt across app restarts */
interface CsfdRatingQueueStore {
    fun load(): Map<Int, QueuedCsfdRating>

    fun save(queue: Map<Int, QueuedCsfdRating>)
}

private class SharedPrefsCsfdRatingQueueStore(
    private val context: Context,
) : CsfdRatingQueueStore {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun load(): Map<Int, QueuedCsfdRating> =
        prefs
            .getString(KEY, null)
            .orEmpty()
            .split(',')
            .mapNotNull { entry ->
                val parts = entry.split(':').mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) parts[0] to QueuedCsfdRating(parts[1], parts[2]) else null
            }.toMap()

    override fun save(queue: Map<Int, QueuedCsfdRating>) {
        prefs
            .edit()
            .putString(KEY, queue.entries.joinToString(",") { (id, q) -> "$id:${q.stars}:${q.attempts}" })
            .apply()
    }

    companion object {
        private const val PREFS = "csfd_rating_queue"
        private const val KEY = "queue"
    }
}

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

        /** Message for a rating kept for later; replaceable in tests */
        internal var queuedText: (String) -> String = { context.getString(R.string.csfd_rating_queued, it) }

        /** Message for a rating given up after [MAX_ATTEMPTS]; replaceable in tests */
        internal var gaveUpText: (String) -> String = { context.getString(R.string.csfd_rating_gave_up, MAX_ATTEMPTS, it) }

        /** Replaceable in tests; read on first use so a test can swap it before */
        internal var store: CsfdRatingQueueStore = SharedPrefsCsfdRatingQueueStore(context)

        /** Ratings that failed (e.g. the plugin's ČSFD login is in its 10 min pause) and are sent again later */
        private var queuedRatings: Map<Int, QueuedCsfdRating>? = null
        private var retryJob: Job? = null

        private val lock = Any()

        // All guarded by lock
        private val debounceJobs = mutableMapOf<Int, Job>()
        private val latestStars = mutableMapOf<Int, Int>()
        private val generations = mutableMapOf<Int, Int>()
        private val revertTo = mutableMapOf<Int, Int?>()

        private val sendMutex = Mutex()
        private var lastSentAtNanos: Long? = null

        init {
            // Loads the queue off the main thread; ratings left from the last run go out shortly after the start
            scope.launch(ExceptionHandler()) {
                if (queue().isNotEmpty()) scheduleRetry(STARTUP_DELAY)
            }
        }

        /** The choice waiting to be sent for [csfdId], if any, so a reopened page shows it instead of the old rating */
        fun pendingStars(csfdId: Int): Int? = synchronized(lock) { latestStars[csfdId] ?: queue()[csfdId]?.stars }

        private fun queue(): Map<Int, QueuedCsfdRating> =
            synchronized(lock) {
                queuedRatings ?: runCatching { store.load() }
                    .onFailure { Timber.w(it, "Could not load the ČSFD rating queue") }
                    .getOrDefault(emptyMap())
                    .also { queuedRatings = it }
            }

        private fun updateQueue(change: (Map<Int, QueuedCsfdRating>) -> Map<Int, QueuedCsfdRating>) {
            synchronized(lock) {
                val updated = change(queue())
                queuedRatings = updated
                runCatching { store.save(updated) }.onFailure { Timber.w(it, "Could not save the ČSFD rating queue") }
            }
        }

        /**
         * Keeps a rating that could not be sent (e.g. from the prompt after watching) to send it again later.
         * Returns the message for the user.
         */
        fun queueFailed(
            csfdId: Int,
            stars: Int,
            error: String,
        ): String {
            val attempts = (queue()[csfdId]?.takeIf { it.stars == stars }?.attempts ?: 0) + 1
            return if (attempts >= MAX_ATTEMPTS) {
                updateQueue { it - csfdId }
                Timber.w("ČSFD rating %s → %s given up after %s attempts: %s", csfdId, stars, attempts, error)
                gaveUpText(error)
            } else {
                updateQueue { it + (csfdId to QueuedCsfdRating(stars, attempts)) }
                Timber.i("ČSFD rating %s → %s queued for a retry (attempt %s): %s", csfdId, stars, attempts, error)
                scheduleRetry()
                queuedText(error)
            }
        }

        /** Only one retry is waiting or running at a time, so nothing is sent twice */
        private fun scheduleRetry(after: Duration = RETRY_DELAY) {
            synchronized(lock) {
                if (retryJob?.isActive == true) return
                retryJob =
                    scope.launch(ExceptionHandler()) {
                        delay(after)
                        // Cleared first, so a failure during this attempt can schedule the next one
                        synchronized(lock) { retryJob = null }
                        retryQueued()
                    }
            }
        }

        /** Sends the queued ratings again, one at a time */
        private suspend fun retryQueued() {
            for ((csfdId, queued) in queue()) {
                // A newer choice from the detail page replaced it
                if (synchronized(lock) { latestStars.containsKey(csfdId) }) continue
                val error = sendNow(csfdId, queued.stars)
                if (error == null) {
                    updateQueue { it - csfdId }
                    Timber.i("Queued ČSFD rating %s → %s sent", csfdId, queued.stars)
                    _results.emit(CsfdRatingResult(csfdId, queued.stars, null, null))
                    showMessage(null)
                } else {
                    // A newer choice made while this was being sent replaces it, the old stars must not come back
                    val replaced =
                        synchronized(lock) { queue()[csfdId] != queued || latestStars.containsKey(csfdId) }
                    if (replaced) {
                        Timber.i("Queued ČSFD rating %s → %s failed but was replaced meanwhile", csfdId, queued.stars)
                        continue
                    }
                    val message = queueFailed(csfdId, queued.stars, error)
                    val gaveUp = csfdId !in queue()
                    if (gaveUp) {
                        // revertTo is not known any more, the page reloads the rating from ČSFD
                        _results.emit(CsfdRatingResult(csfdId, queued.stars, null, message))
                        showMessage(message)
                    }
                }
            }
        }

        private suspend fun sendNow(
            csfdId: Int,
            stars: Int,
        ): String? =
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
                // A new choice replaces one waiting in the queue
                if (queue().containsKey(csfdId)) updateQueue { it - csfdId }
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
            val error = sendNow(csfdId, stars)
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
                if (queue().containsKey(csfdId)) updateQueue { it - csfdId }
                _results.emit(result)
                showMessage(null)
            } else {
                Timber.w("ČSFD rating %s → %s failed: %s", csfdId, stars, result.error)
                // Not lost: kept and sent again later, the page keeps showing the new stars
                val message = queueFailed(csfdId, stars, result.error)
                val queued = csfdId in queue()
                _results.emit(result.copy(error = message, queued = queued))
                showMessage(message)
            }
        }

        companion object {
            val DEBOUNCE = 600.milliseconds

            /** The plugin's throttle is 1 s, with some margin */
            val MIN_INTERVAL = 1100.milliseconds

            /** The plugin pauses ČSFD logins for 10 min after a failed one, so retry a bit after that */
            val RETRY_DELAY = 11.minutes

            /** Queued ratings from the last run are sent this long after the start */
            val STARTUP_DELAY = 30.seconds

            /** Failed attempts after which a rating is dropped (e.g. a wrong password that does not fix itself) */
            const val MAX_ATTEMPTS = 3

            private fun Long.nanosToMillis() = this / 1_000_000
        }
    }
